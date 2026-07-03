package co.edu.elecciones.service;

import co.edu.elecciones.config.BusinessConflictException;
import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionResultSummary;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.domain.ResultValidationStatus;
import co.edu.elecciones.dto.Requests.ElectionResultSummaryRequest;
import co.edu.elecciones.dto.Requests.OfficialResultRequest;
import co.edu.elecciones.dto.Responses.OfficialResultPage;
import co.edu.elecciones.dto.Responses.OfficialResultResponse;
import co.edu.elecciones.dto.Responses.ResultCandidateResponse;
import co.edu.elecciones.dto.Responses.ResultElectionResponse;
import co.edu.elecciones.dto.Responses.ResultImportResponse;
import co.edu.elecciones.dto.Responses.ResultManagement;
import co.edu.elecciones.dto.Responses.ResultManagementCounters;
import co.edu.elecciones.dto.Responses.ResultPartyResponse;
import co.edu.elecciones.dto.Responses.ResultSummaryResponse;
import co.edu.elecciones.dto.Responses.ResultStatusOption;
import co.edu.elecciones.dto.Responses.ResultValidationResponse;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ResultManagementService {
    private static final long MAX_CSV_SIZE = 5L * 1024L * 1024L;
    private static final List<String> CSV_HEADER_ORDER = List.of(
            "electionId", "candidateId", "department", "municipality", "votes",
            "reportedTables", "totalTables", "participation", "source"
    );
    private static final Set<String> CSV_HEADERS = Set.copyOf(CSV_HEADER_ORDER);

    private final OfficialResultRepository results;
    private final ElectionResultSummaryRepository summaries;
    private final ElectionRepository elections;
    private final CandidateRepository candidates;

    public ResultManagementService(
            OfficialResultRepository results,
            ElectionResultSummaryRepository summaries,
            ElectionRepository elections,
            CandidateRepository candidates
    ) {
        this.results = results;
        this.summaries = summaries;
        this.elections = elections;
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public ResultManagement management(
            Long electionId,
            ResultValidationStatus status,
            String department,
            String municipality,
            String search,
            int page,
            int size
    ) {
        validatePage(page, size);
        List<Election> electionEntities = elections.selectAll();
        List<ResultElectionResponse> electionOptions = electionEntities.stream()
                .map(this::toElectionResponse)
                .toList();

        if (electionEntities.isEmpty()) {
            return emptyManagement(page, size, electionOptions);
        }

        Long selectedElectionId = electionId == null ? electionEntities.get(0).id : electionId;
        Election selectedElection = electionEntities.stream()
                .filter(item -> Objects.equals(item.id, selectedElectionId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("La elección seleccionada no existe"));

        String normalizedDepartment = normalizeOptional(department);
        String normalizedMunicipality = normalizeOptional(municipality);
        String normalizedSearch = normalizeOptional(search);

        Page<OfficialResult> resultPage = results.selectPage(
                selectedElectionId,
                status,
                nullToEmpty(normalizedDepartment),
                nullToEmpty(normalizedMunicipality),
                nullToEmpty(normalizedSearch),
                PageRequest.of(page, size)
        );

        OfficialResultRepository.ManagementAggregate aggregate = results.selectManagementAggregate(
                selectedElectionId,
                ResultValidationStatus.VALIDADO,
                ResultValidationStatus.PENDIENTE,
                ResultValidationStatus.RECHAZADO
        );
        List<OfficialResultRepository.TerritoryTables> territories =
                results.selectTerritoryTables(selectedElectionId);
        ElectionResultSummary summary = summaries.selectByElectionId(selectedElectionId).orElse(null);

        long records = safe(aggregate == null ? null : aggregate.getRecords());
        long candidateVotes = safe(aggregate == null ? null : aggregate.getCandidateVotes());
        long validated = safe(aggregate == null ? null : aggregate.getValidated());
        long pending = safe(aggregate == null ? null : aggregate.getPending());
        long rejected = safe(aggregate == null ? null : aggregate.getRejected());
        Instant lastImportedAt = aggregate == null ? null : aggregate.getLastImportedAt();

        TableMetrics tableMetrics = tableMetrics(summary, territories);
        double participation = participation(summary, territories);
        Reconciliation reconciliation = reconciliation(summary, candidateVotes);
        String traceability = traceability(records, pending, rejected, summary, lastImportedAt);

        List<ResultCandidateResponse> candidateOptions = candidates.selectByElectionId(selectedElection.id).stream()
                .map(this::toCandidateResponse)
                .toList();

        List<String> departments = results.selectDepartments(selectedElection.id);
        List<String> municipalities = results.selectMunicipalities(
                selectedElection.id,
                nullToEmpty(normalizedDepartment)
        );

        ResultManagementCounters counters = new ResultManagementCounters(
                records,
                candidateVotes,
                tableMetrics.reported(),
                tableMetrics.total(),
                percentage(tableMetrics.reported(), tableMetrics.total()),
                round(participation),
                validated,
                pending,
                rejected,
                traceability,
                reconciliation.difference(),
                reconciliation.reconciled(),
                lastImportedAt
        );

        return new ResultManagement(
                selectedElectionId,
                counters,
                toSummaryResponse(summary),
                new OfficialResultPage(
                        resultPage.getContent().stream().map(this::toResponse).toList(),
                        resultPage.getNumber(),
                        resultPage.getSize(),
                        resultPage.getTotalElements(),
                        resultPage.getTotalPages()
                ),
                electionOptions,
                candidateOptions,
                validationStatusOptions(),
                departments,
                municipalities,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<OfficialResultResponse> selectAll(Long electionId, String department) {
        List<OfficialResult> selected;
        if (electionId != null) {
            selected = results.selectByElectionId(electionId);
        } else if (department != null && !department.isBlank()) {
            selected = results.selectByDepartment(department.trim());
        } else {
            selected = results.selectAll();
        }
        return selected.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OfficialResultResponse selectById(Long id) {
        return toResponse(findResult(id));
    }

    @Transactional(readOnly = true)
    public ResultSummaryResponse selectSummary(Long electionId) {
        findElection(electionId);
        ElectionResultSummary summary = summaries.selectByElectionId(electionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No existe un consolidado para la elección seleccionada"
                ));
        return toSummaryResponse(summary);
    }

    @Transactional
    public OfficialResultResponse create(OfficialResultRequest request) {
        SaveOutcome outcome = saveInternal(null, request, "CARGA_MANUAL", false);
        recalculateScope(outcome.newScope());
        return toResponse(findResult(outcome.result().id));
    }

    @Transactional
    public OfficialResultResponse update(Long id, OfficialResultRequest request) {
        OfficialResult existing = findResult(id);
        Scope previousScope = Scope.from(existing);
        SaveOutcome outcome = saveInternal(existing, request, "CARGA_MANUAL", false);
        if (!previousScope.equals(outcome.newScope())) {
            recalculateScope(previousScope);
        }
        recalculateScope(outcome.newScope());
        return toResponse(findResult(id));
    }

    @Transactional
    public void delete(Long id) {
        OfficialResult existing = findResult(id);
        if (existing.election != null && existing.election.state == ElectionState.ARCHIVADA) {
            throw new IllegalArgumentException("No se pueden eliminar resultados de una elección archivada");
        }
        Scope scope = Scope.from(existing);
        if (results.deleteByIdStatement(id) == 0) {
            throw new EntityNotFoundException("El resultado oficial no existe");
        }
        recalculateScope(scope);
    }

    @Transactional
    public ResultSummaryResponse upsertSummary(ElectionResultSummaryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El resumen electoral es obligatorio");
        }
        validateSummaryRequest(request);
        Election election = findElection(request.electionId());
        if (election.state == ElectionState.ARCHIVADA) {
            throw new IllegalArgumentException("No se puede modificar el consolidado de una elección archivada");
        }

        ElectionResultSummary summary = summaries.selectByElectionId(election.id)
                .orElseGet(ElectionResultSummary::new);
        summary.election = election;
        summary.eligibleVoters = request.eligibleVoters();
        summary.totalVoters = request.totalVoters();
        summary.validVotes = request.validVotes();
        summary.blankVotes = request.blankVotes();
        summary.nullVotes = request.nullVotes();
        summary.unmarkedVotes = request.unmarkedVotes();
        summary.reportedTables = request.reportedTables();
        summary.totalTables = request.totalTables();
        summary.source = normalizeSource(request.source(), "CARGA_MANUAL");
        summary.importedAt = request.importedAt() == null ? Instant.now() : request.importedAt();
        validateSummary(summary);
        return toSummaryResponse(summaries.save(summary));
    }

    @Transactional
    public ResultValidationResponse validateElection(Long electionId) {
        findElection(electionId);
        List<OfficialResult> electionResults = results.selectByElectionId(electionId);
        Set<Scope> scopes = new LinkedHashSet<>();
        Map<Scope, String> scopeErrors = scopeValidationErrors(electionResults);
        long validated = 0;
        long rejected = 0;
        Instant now = Instant.now();
        String username = currentUsername();

        for (OfficialResult result : electionResults) {
            Scope scope = result.election == null ? null : Scope.from(result);
            String validationMessage = validatePersistedResult(result);
            if (validationMessage == null && scope != null) {
                validationMessage = scopeErrors.get(scope);
            }
            result.validationStatus = validationMessage == null
                    ? ResultValidationStatus.VALIDADO
                    : ResultValidationStatus.RECHAZADO;
            result.validationMessage = validationMessage == null
                    ? "Validaciones de integridad superadas"
                    : validationMessage;
            result.validatedAt = now;
            result.validatedBy = username;
            if (result.validationStatus == ResultValidationStatus.VALIDADO) {
                validated++;
            } else {
                rejected++;
            }
            if (scope != null) {
                scopes.add(scope);
            }
        }
        results.saveAll(electionResults);
        scopes.forEach(this::recalculateScope);
        return new ResultValidationResponse(validated, rejected, scopes.size());
    }

    @Transactional
    public ResultImportResponse importCsv(MultipartFile file) {
        validateCsvFile(file);
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .build();

            try (CSVParser parser = format.parse(new StringReader(content))) {
                validateHeaders(parser);
                Set<String> fileKeys = new HashSet<>();
                Set<Scope> scopes = new LinkedHashSet<>();
                int created = 0;
                int updated = 0;
                int processed = 0;

                for (CSVRecord record : parser) {
                    long line = record.getRecordNumber() + 1;
                    OfficialResultRequest request = parseCsvRecord(record, line);
                    String key = naturalKey(request);
                    if (!fileKeys.add(key)) {
                        throw new BusinessConflictException(
                                "La fila " + line + " repite la misma elección, candidato y territorio dentro del archivo"
                        );
                    }
                    String source = normalizeSource(record.get("source"), "IMPORTACION_CSV");
                    SaveOutcome outcome = saveInternal(null, request, source, true);
                    scopes.add(outcome.newScope());
                    if (outcome.created()) {
                        created++;
                    } else {
                        updated++;
                    }
                    processed++;
                }

                if (processed == 0) {
                    throw new IllegalArgumentException("El archivo CSV no contiene resultados para importar");
                }
                scopes.forEach(this::recalculateScope);
                return new ResultImportResponse(created, updated, processed);
            }
        } catch (IllegalArgumentException | BusinessConflictException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("No fue posible leer el archivo CSV. Verifica su formato", exception);
        }
    }

    private ResultManagement emptyManagement(
            int page,
            int size,
            List<ResultElectionResponse> elections
    ) {
        return new ResultManagement(
                null,
                new ResultManagementCounters(
                        0, 0, 0, 0, 0, 0,
                        0, 0, 0, "SIN_DATOS", 0, false, null
                ),
                null,
                new OfficialResultPage(List.of(), page, size, 0, 0),
                elections,
                List.of(),
                validationStatusOptions(),
                List.of(),
                List.of(),
                Instant.now()
        );
    }

    private SaveOutcome saveInternal(
            OfficialResult existing,
            OfficialResultRequest request,
            String defaultSource,
            boolean allowNaturalKeyUpdate
    ) {
        validateRequest(request);
        Election election = findElection(request.electionId());
        Candidate candidate = candidates.selectById(request.candidateId())
                .orElseThrow(() -> new EntityNotFoundException("El candidato seleccionado no existe"));

        if (candidate.election == null || !Objects.equals(candidate.election.id, election.id)) {
            throw new IllegalArgumentException("El candidato no pertenece a la elección seleccionada");
        }
        if (election.state == ElectionState.ARCHIVADA) {
            throw new IllegalArgumentException("No se pueden modificar resultados de una elección archivada");
        }
        if (existing == null && !candidate.active) {
            throw new IllegalArgumentException("No se pueden registrar resultados nuevos para un candidato inactivo");
        }

        String department = normalizeOptional(request.department());
        String municipality = normalizeOptional(request.municipality());
        validateTerritory(department, municipality);
        OfficialResult naturalKeyResult = results.selectByNaturalKey(
                election.id,
                candidate.id,
                department,
                municipality
        ).orElse(null);
        Long consistencyExcludeId = existing != null
                ? existing.id
                : naturalKeyResult == null ? null : naturalKeyResult.id;
        validateScopeConsistency(
                election.id,
                department,
                municipality,
                request.reportedTables(),
                request.totalTables(),
                request.participation(),
                consistencyExcludeId
        );

        OfficialResult entity = existing;
        boolean created = false;
        if (entity == null) {
            entity = naturalKeyResult;
            if (entity != null && !allowNaturalKeyUpdate) {
                throw new BusinessConflictException(
                        "Ya existe un resultado para el mismo candidato, elección y territorio"
                );
            }
            created = entity == null;
            if (entity == null) {
                entity = new OfficialResult();
            }
        } else if (naturalKeyResult != null && !Objects.equals(naturalKeyResult.id, entity.id)) {
            throw new BusinessConflictException(
                    "Ya existe otro resultado para el mismo candidato, elección y territorio"
            );
        }

        entity.election = election;
        entity.candidate = candidate;
        entity.department = department;
        entity.municipality = municipality;
        entity.votes = request.votes();
        entity.reportedTables = request.reportedTables();
        entity.totalTables = request.totalTables();
        entity.participation = round(request.participation());
        entity.source = normalizeSource(request.source(), defaultSource);
        entity.importedAt = Instant.now();
        entity.validationStatus = ResultValidationStatus.VALIDADO;
        entity.validationMessage = "Validaciones de integridad superadas";
        entity.validatedAt = Instant.now();
        entity.validatedBy = currentUsername();

        OfficialResult saved = results.save(entity);
        return new SaveOutcome(saved, created, Scope.from(saved));
    }

    private void validateRequest(OfficialResultRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El resultado oficial es obligatorio");
        }
        if (request.electionId() == null || request.electionId() <= 0) {
            throw new IllegalArgumentException("Selecciona una elección válida");
        }
        if (request.candidateId() == null || request.candidateId() <= 0) {
            throw new IllegalArgumentException("Selecciona un candidato válido");
        }
        if (request.votes() == null || request.votes() < 0) {
            throw new IllegalArgumentException("Los votos no pueden ser negativos");
        }
        if (request.reportedTables() == null || request.reportedTables() < 0
                || request.totalTables() == null || request.totalTables() < 0) {
            throw new IllegalArgumentException("Las mesas deben ser valores no negativos");
        }
        if (request.reportedTables() > request.totalTables()) {
            throw new IllegalArgumentException("Las mesas reportadas no pueden superar las mesas totales");
        }
        if (request.participation() == null
                || request.participation() < 0
                || request.participation() > 100) {
            throw new IllegalArgumentException("La participación debe estar entre 0 y 100");
        }
    }

    private void validateScopeConsistency(
            Long electionId,
            String department,
            String municipality,
            Integer reportedTables,
            Integer totalTables,
            Double participation,
            Long excludeId
    ) {
        for (OfficialResult result : results.selectByScope(electionId, department, municipality)) {
            if (Objects.equals(result.id, excludeId)) {
                continue;
            }
            if (!Objects.equals(result.reportedTables, reportedTables)
                    || !Objects.equals(result.totalTables, totalTables)
                    || Math.abs(safe(result.participation) - participation) > 0.001) {
                throw new BusinessConflictException(
                        "Las mesas y la participación deben coincidir para todos los candidatos del mismo territorio"
                );
            }
        }
    }

    private void recalculateScope(Scope scope) {
        List<OfficialResult> scopeResults = results.selectByScope(
                scope.electionId(),
                scope.department(),
                scope.municipality()
        );
        long totalVotes = scopeResults.stream()
                .filter(item -> item.validationStatus == ResultValidationStatus.VALIDADO)
                .mapToLong(item -> safe(item.votes))
                .sum();
        for (OfficialResult result : scopeResults) {
            result.percentage = totalVotes <= 0 || result.validationStatus != ResultValidationStatus.VALIDADO
                    ? 0.0
                    : round(result.votes * 100.0 / totalVotes);
        }
        if (!scopeResults.isEmpty()) {
            results.saveAll(scopeResults);
        }
    }

    private String validatePersistedResult(OfficialResult result) {
        if (result.election == null || result.candidate == null || result.candidate.election == null
                || !Objects.equals(result.election.id, result.candidate.election.id)) {
            return "El candidato no pertenece a la elección del resultado";
        }
        if (result.votes == null || result.votes < 0) {
            return "La cantidad de votos es inválida";
        }
        if (result.reportedTables == null || result.totalTables == null
                || result.reportedTables < 0 || result.totalTables < 0
                || result.reportedTables > result.totalTables) {
            return "La relación de mesas reportadas y totales es inválida";
        }
        if (result.participation == null || result.participation < 0 || result.participation > 100) {
            return "La participación está fuera del rango permitido";
        }
        if ((result.municipality != null && !result.municipality.isBlank())
                && (result.department == null || result.department.isBlank())) {
            return "Un municipio debe estar asociado a un departamento";
        }
        if (result.source == null || result.source.isBlank()) {
            return "El origen del resultado es obligatorio";
        }
        return null;
    }

    private Map<Scope, String> scopeValidationErrors(List<OfficialResult> electionResults) {
        Map<Scope, List<OfficialResult>> grouped = new LinkedHashMap<>();
        for (OfficialResult result : electionResults) {
            if (result.election == null) {
                continue;
            }
            grouped.computeIfAbsent(Scope.from(result), ignored -> new ArrayList<>()).add(result);
        }

        Map<Scope, String> errors = new LinkedHashMap<>();
        for (Map.Entry<Scope, List<OfficialResult>> entry : grouped.entrySet()) {
            List<OfficialResult> scopeResults = entry.getValue();
            if (scopeResults.size() < 2) {
                continue;
            }
            OfficialResult reference = scopeResults.get(0);
            boolean inconsistent = scopeResults.stream().skip(1).anyMatch(result ->
                    !Objects.equals(reference.reportedTables, result.reportedTables)
                            || !Objects.equals(reference.totalTables, result.totalTables)
                            || Math.abs(safe(reference.participation) - safe(result.participation)) > 0.001
            );
            if (inconsistent) {
                errors.put(
                        entry.getKey(),
                        "Las mesas y la participación no coinciden entre candidatos del mismo territorio"
                );
            }
        }
        return errors;
    }

    private void validateSummaryRequest(ElectionResultSummaryRequest request) {
        if (request.electionId() == null || request.electionId() <= 0) {
            throw new IllegalArgumentException("Selecciona una elección válida");
        }
        if (request.eligibleVoters() == null || request.totalVoters() == null
                || request.validVotes() == null || request.blankVotes() == null
                || request.nullVotes() == null || request.unmarkedVotes() == null
                || request.reportedTables() == null || request.totalTables() == null) {
            throw new IllegalArgumentException("Todos los valores del consolidado son obligatorios");
        }
        if (request.eligibleVoters() < 0 || request.totalVoters() < 0
                || request.validVotes() < 0 || request.blankVotes() < 0
                || request.nullVotes() < 0 || request.unmarkedVotes() < 0
                || request.reportedTables() < 0 || request.totalTables() < 0) {
            throw new IllegalArgumentException("Los valores del consolidado no pueden ser negativos");
        }
        if (request.importedAt() != null && request.importedAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("La fecha de corte no puede estar en el futuro");
        }
    }

    private void validateSummary(ElectionResultSummary summary) {
        if (summary.reportedTables > summary.totalTables) {
            throw new IllegalArgumentException("Las mesas reportadas no pueden superar las mesas totales");
        }
        if (summary.eligibleVoters > 0 && summary.totalVoters > summary.eligibleVoters) {
            throw new IllegalArgumentException("Los sufragantes no pueden superar el potencial electoral");
        }
        if (summary.totalVoters > 0
                && summary.validVotes + summary.nullVotes + summary.unmarkedVotes > summary.totalVoters) {
            throw new IllegalArgumentException("El desglose de votos supera el total de sufragantes");
        }
        if (summary.blankVotes > summary.validVotes) {
            throw new IllegalArgumentException("Los votos en blanco no pueden superar los votos válidos");
        }
    }

    private void validateTerritory(String department, String municipality) {
        if (municipality != null && department == null) {
            throw new IllegalArgumentException("Selecciona el departamento del municipio");
        }
        if (department != null && department.length() > 120) {
            throw new IllegalArgumentException("El departamento no puede superar 120 caracteres");
        }
        if (municipality != null && municipality.length() > 120) {
            throw new IllegalArgumentException("El municipio no puede superar 120 caracteres");
        }
    }

    private void validateCsvFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un archivo CSV con contenido");
        }
        if (file.getSize() > MAX_CSV_SIZE) {
            throw new IllegalArgumentException("El archivo CSV no puede superar 5 MB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("El archivo debe tener extensión .csv");
        }
    }

    private void validateHeaders(CSVParser parser) {
        Set<String> headers = parser.getHeaderMap().keySet();
        if (!headers.equals(CSV_HEADERS)) {
            throw new IllegalArgumentException(
                    "Las columnas del CSV deben ser: " + String.join(",", CSV_HEADER_ORDER)
            );
        }
    }

    private OfficialResultRequest parseCsvRecord(CSVRecord record, long line) {
        try {
            return new OfficialResultRequest(
                    parseLong(record.get("electionId"), "electionId", line),
                    parseLong(record.get("candidateId"), "candidateId", line),
                    record.get("department"),
                    record.get("municipality"),
                    parseLong(record.get("votes"), "votes", line),
                    parseInteger(record.get("reportedTables"), "reportedTables", line),
                    parseInteger(record.get("totalTables"), "totalTables", line),
                    parseDouble(record.get("participation"), "participation", line),
                    record.get("source")
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La fila " + line + " contiene un valor numérico inválido");
        }
    }

    private Long parseLong(String value, String field, long line) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("La fila " + line + " no contiene " + field);
        }
        return Long.valueOf(value.trim());
    }

    private Integer parseInteger(String value, String field, long line) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("La fila " + line + " no contiene " + field);
        }
        return Integer.valueOf(value.trim());
    }

    private Double parseDouble(String value, String field, long line) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("La fila " + line + " no contiene " + field);
        }
        return Double.valueOf(value.trim().replace(',', '.'));
    }

    private String naturalKey(OfficialResultRequest request) {
        return request.electionId() + "|" + request.candidateId() + "|"
                + nullToEmpty(normalizeOptional(request.department())).toLowerCase(Locale.ROOT) + "|"
                + nullToEmpty(normalizeOptional(request.municipality())).toLowerCase(Locale.ROOT);
    }

    private Election findElection(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Selecciona una elección válida");
        }
        return elections.selectById(id)
                .orElseThrow(() -> new EntityNotFoundException("La elección seleccionada no existe"));
    }

    private OfficialResult findResult(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El identificador del resultado es inválido");
        }
        return results.selectById(id)
                .orElseThrow(() -> new EntityNotFoundException("El resultado oficial no existe"));
    }

    private OfficialResultResponse toResponse(OfficialResult result) {
        return new OfficialResultResponse(
                result.id,
                result.createdAt,
                result.updatedAt,
                toElectionResponse(result.election),
                toCandidateResponse(result.candidate),
                result.department,
                result.municipality,
                safe(result.votes),
                round(safe(result.percentage)),
                safe(result.reportedTables),
                safe(result.totalTables),
                round(safe(result.participation)),
                result.source,
                result.importedAt,
                result.validationStatus == null ? ResultValidationStatus.PENDIENTE.name() : result.validationStatus.name(),
                result.validationMessage,
                result.validatedAt,
                result.validatedBy
        );
    }

    private ResultElectionResponse toElectionResponse(Election election) {
        return new ResultElectionResponse(
                election.id,
                election.name,
                election.type == null ? null : election.type.name(),
                election.round == null ? null : election.round.name(),
                election.electionDate,
                election.state == null ? null : election.state.name()
        );
    }

    private ResultCandidateResponse toCandidateResponse(Candidate candidate) {
        return new ResultCandidateResponse(
                candidate.id,
                candidate.name,
                candidate.active,
                candidate.election == null ? null : candidate.election.id,
                candidate.party == null ? null : new ResultPartyResponse(
                        candidate.party.id,
                        candidate.party.name,
                        candidate.party.acronym,
                        candidate.party.color
                )
        );
    }

    private ResultSummaryResponse toSummaryResponse(ElectionResultSummary summary) {
        if (summary == null) {
            return null;
        }
        return new ResultSummaryResponse(
                summary.id,
                summary.election.id,
                safe(summary.eligibleVoters),
                safe(summary.totalVoters),
                safe(summary.validVotes),
                safe(summary.blankVotes),
                safe(summary.nullVotes),
                safe(summary.unmarkedVotes),
                safe(summary.reportedTables),
                safe(summary.totalTables),
                percentage(safe(summary.reportedTables), safe(summary.totalTables)),
                summary.eligibleVoters > 0
                        ? round(summary.totalVoters * 100.0 / summary.eligibleVoters)
                        : 0,
                summary.source,
                summary.importedAt
        );
    }

    private TableMetrics tableMetrics(
            ElectionResultSummary summary,
            List<OfficialResultRepository.TerritoryTables> territories
    ) {
        if (summary != null && (summary.totalTables > 0 || summary.reportedTables > 0)) {
            return new TableMetrics(summary.reportedTables, summary.totalTables);
        }
        int reported = territories.stream().mapToInt(item -> safe(item.getReportedTables())).sum();
        int total = territories.stream().mapToInt(item -> safe(item.getTotalTables())).sum();
        return new TableMetrics(reported, total);
    }

    private double participation(
            ElectionResultSummary summary,
            List<OfficialResultRepository.TerritoryTables> territories
    ) {
        if (summary != null && summary.eligibleVoters > 0) {
            return summary.totalVoters * 100.0 / summary.eligibleVoters;
        }
        double weighted = 0;
        long weight = 0;
        for (OfficialResultRepository.TerritoryTables territory : territories) {
            int territoryWeight = Math.max(1, safe(territory.getReportedTables()));
            weighted += safe(territory.getParticipation()) * territoryWeight;
            weight += territoryWeight;
        }
        return weight == 0 ? 0 : weighted / weight;
    }

    private Reconciliation reconciliation(ElectionResultSummary summary, long candidateVotes) {
        if (summary == null || summary.validVotes <= 0) {
            return new Reconciliation(0, false);
        }
        long expectedCandidateVotes = Math.max(0, summary.validVotes - summary.blankVotes);
        long difference = candidateVotes - expectedCandidateVotes;
        return new Reconciliation(difference, difference == 0);
    }

    private String traceability(
            long records,
            long pending,
            long rejected,
            ElectionResultSummary summary,
            Instant lastImportedAt
    ) {
        if (records == 0) {
            return "SIN_DATOS";
        }
        if (pending > 0 || rejected > 0) {
            return "REQUIERE_REVISION";
        }
        if (summary == null || lastImportedAt == null) {
            return "INCOMPLETA";
        }
        return "COMPLETA";
    }

    private List<ResultStatusOption> validationStatusOptions() {
        return List.of(
                new ResultStatusOption(ResultValidationStatus.VALIDADO.name(), "Validado"),
                new ResultStatusOption(ResultValidationStatus.PENDIENTE.name(), "Pendiente"),
                new ResultStatusOption(ResultValidationStatus.RECHAZADO.name(), "Rechazado")
        );
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 100");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeSource(String value, String fallback) {
        String normalized = normalizeOptional(value);
        String source = normalized == null ? fallback : normalized;
        if (source.length() > 160) {
            throw new IllegalArgumentException("El origen no puede superar 160 caracteres");
        }
        return source;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    private long safe(Long value) {
        return value == null ? 0 : value;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private double safe(Double value) {
        return value == null ? 0 : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double percentage(int reported, int total) {
        return total <= 0 ? 0 : round(reported * 100.0 / total);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Scope(Long electionId, String department, String municipality) {
        private static Scope from(OfficialResult result) {
            return new Scope(result.election.id, result.department, result.municipality);
        }
    }

    private record SaveOutcome(OfficialResult result, boolean created, Scope newScope) {
    }

    private record TableMetrics(int reported, int total) {
    }

    private record Reconciliation(long difference, boolean reconciled) {
    }
}
