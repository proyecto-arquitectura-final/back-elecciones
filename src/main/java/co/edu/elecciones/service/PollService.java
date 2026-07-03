package co.edu.elecciones.service;

import co.edu.elecciones.config.BusinessConflictException;
import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.Poll;
import co.edu.elecciones.domain.PollResult;
import co.edu.elecciones.domain.PollStatus;
import co.edu.elecciones.dto.Requests.PollRequest;
import co.edu.elecciones.dto.Requests.PollResultRequest;
import co.edu.elecciones.dto.Responses.PollCandidateResponse;
import co.edu.elecciones.dto.Responses.PollElectionResponse;
import co.edu.elecciones.dto.Responses.PollImportResponse;
import co.edu.elecciones.dto.Responses.PollManagement;
import co.edu.elecciones.dto.Responses.PollManagementCounters;
import co.edu.elecciones.dto.Responses.PollPage;
import co.edu.elecciones.dto.Responses.PollPartyResponse;
import co.edu.elecciones.dto.Responses.PollResponse;
import co.edu.elecciones.dto.Responses.PollResultResponse;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.PollRepository;
import co.edu.elecciones.repository.PollResultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PollService {
    private static final long MAX_CSV_SIZE = 5L * 1024L * 1024L;
    private static final Set<String> CSV_HEADERS = Set.of(
            "electionId", "source", "date", "sampleSize", "marginError",
            "methodology", "status", "candidateId", "percentage"
    );

    private final PollRepository polls;
    private final PollResultRepository pollResults;
    private final CandidateRepository candidates;
    private final ElectionRepository elections;

    public PollService(
            PollRepository polls,
            PollResultRepository pollResults,
            CandidateRepository candidates,
            ElectionRepository elections
    ) {
        this.polls = polls;
        this.pollResults = pollResults;
        this.candidates = candidates;
        this.elections = elections;
    }

    @Transactional(readOnly = true)
    public List<PollResponse> selectAll() {
        return mapPolls(polls.selectAll());
    }

    @Transactional(readOnly = true)
    public PollManagement management(
            Long electionId,
            PollStatus status,
            String search,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 100");
        }
        if (electionId != null && elections.selectById(electionId).isEmpty()) {
            throw new EntityNotFoundException("La elección seleccionada no existe");
        }

        String normalizedSearch = normalizeOptional(search);
        Page<Poll> pollPage = polls.selectPage(
                electionId,
                status,
                normalizedSearch == null ? "" : normalizedSearch,
                PageRequest.of(page, size)
        );
        List<PollResponse> items = mapPolls(pollPage.getContent());
        PollRepository.CountersRow row = polls.selectCounters(electionId);

        PollManagementCounters counters = new PollManagementCounters(
                safe(row == null ? null : row.getTotal()),
                safe(row == null ? null : row.getApproved()),
                safe(row == null ? null : row.getPending()),
                safe(row == null ? null : row.getRejected()),
                round(row == null || row.getAverageSample() == null ? 0 : row.getAverageSample())
        );

        List<PollElectionResponse> electionOptions = elections.selectAll().stream()
                .map(this::toElectionResponse)
                .toList();
        List<PollCandidateResponse> candidateOptions = candidates.selectAll().stream()
                .map(this::toCandidateResponse)
                .toList();

        return new PollManagement(
                counters,
                new PollPage(items, pollPage.getNumber(), pollPage.getSize(),
                        pollPage.getTotalElements(), pollPage.getTotalPages()),
                electionOptions,
                candidateOptions,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public PollResponse selectById(Long id) {
        Poll poll = findPoll(id);
        return toResponse(poll, pollResults.selectByPollId(id));
    }

    @Transactional
    public PollResponse create(PollRequest request) {
        return createInternal(request);
    }

    @Transactional
    public PollResponse update(Long id, PollRequest request) {
        Poll poll = findPoll(id);
        List<ResolvedResult> resolvedResults = apply(poll, request, id);
        Poll savedPoll = polls.save(poll);
        pollResults.deleteByPollIdStatement(id);
        List<PollResult> savedResults = saveResults(savedPoll, resolvedResults);
        return toResponse(savedPoll, savedResults);
    }

    @Transactional
    public void delete(Long id) {
        findPoll(id);
        pollResults.deleteByPollIdStatement(id);
        if (polls.deleteByIdStatement(id) == 0) {
            throw new EntityNotFoundException("La encuesta no existe");
        }
    }

    @Transactional
    public PollImportResponse importCsv(MultipartFile file) {
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
                Map<ImportKey, ImportAccumulator> imports = parseImports(parser);
                if (imports.isEmpty()) {
                    throw new IllegalArgumentException("El archivo CSV no contiene registros para importar");
                }

                int resultCount = 0;
                for (ImportAccumulator item : imports.values()) {
                    createInternal(item.toRequest());
                    resultCount += item.results.size();
                }
                return new PollImportResponse(imports.size(), resultCount);
            }
        } catch (IllegalArgumentException | BusinessConflictException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("No fue posible leer el archivo CSV. Verifica su formato", exception);
        }
    }

    private PollResponse createInternal(PollRequest request) {
        Poll poll = new Poll();
        List<ResolvedResult> resolvedResults = apply(poll, request, null);
        Poll savedPoll = polls.save(poll);
        List<PollResult> savedResults = saveResults(savedPoll, resolvedResults);
        return toResponse(savedPoll, savedResults);
    }

    private List<ResolvedResult> apply(Poll poll, PollRequest request, Long currentId) {
        validateRequest(request);
        String source = normalizeRequired(request.source(), "La fuente de la encuesta es obligatoria");
        String methodology = normalizeRequired(request.methodology(), "La metodología es obligatoria");
        validateLength(source, 160, "La fuente no puede superar 160 caracteres");
        validateLength(methodology, 500, "La metodología no puede superar 500 caracteres");

        Election election = elections.selectById(request.electionId())
                .orElseThrow(() -> new EntityNotFoundException("La elección seleccionada no existe"));

        if (election.state == ElectionState.ARCHIVADA) {
            throw new IllegalArgumentException("No se pueden registrar encuestas en una elección archivada");
        }
        if (election.electionDate != null && request.date().isAfter(election.electionDate)) {
            throw new IllegalArgumentException("La fecha de la encuesta no puede ser posterior a la elección");
        }
        if (polls.selectDuplicateCount(election.id, source, request.date(), currentId) > 0) {
            throw new BusinessConflictException(
                    "Ya existe una encuesta de esa fuente para la misma elección y fecha"
            );
        }

        List<ResolvedResult> resolvedResults = validateResults(
                request.results(), election.id, request.status()
        );

        poll.election = election;
        poll.source = source;
        poll.date = request.date();
        poll.sampleSize = request.sampleSize();
        poll.marginError = request.marginError();
        poll.methodology = methodology;
        poll.status = request.status();
        return resolvedResults;
    }

    private void validateRequest(PollRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La información de la encuesta es obligatoria");
        }
        if (request.electionId() == null || request.electionId() <= 0) {
            throw new IllegalArgumentException("Selecciona una elección válida");
        }
        if (request.date() == null) {
            throw new IllegalArgumentException("La fecha de la encuesta es obligatoria");
        }
        if (request.date().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de la encuesta no puede estar en el futuro");
        }
        if (request.sampleSize() == null || request.sampleSize() < 1 || request.sampleSize() > 10_000_000) {
            throw new IllegalArgumentException("El tamaño de muestra debe estar entre 1 y 10.000.000");
        }
        if (request.marginError() == null || !Double.isFinite(request.marginError())
                || request.marginError() <= 0 || request.marginError() > 20) {
            throw new IllegalArgumentException("El margen de error debe ser mayor que 0 y no superar 20%");
        }
        if (request.status() == null) {
            throw new IllegalArgumentException("El estado de la encuesta es obligatorio");
        }
        if (request.results() == null || request.results().isEmpty() || request.results().size() > 100) {
            throw new IllegalArgumentException("La encuesta debe contener entre 1 y 100 resultados");
        }
    }

    private List<ResolvedResult> validateResults(
            List<PollResultRequest> results,
            Long electionId,
            PollStatus status
    ) {
        Set<Long> candidateIds = new LinkedHashSet<>();
        double total = 0;

        for (PollResultRequest item : results) {
            if (item == null || item.candidateId() == null || item.candidateId() <= 0) {
                throw new IllegalArgumentException("Cada resultado debe incluir un candidato válido");
            }
            if (item.percentage() == null || !Double.isFinite(item.percentage())
                    || item.percentage() < 0 || item.percentage() > 100) {
                throw new IllegalArgumentException("Cada porcentaje debe estar entre 0 y 100");
            }
            if (!candidateIds.add(item.candidateId())) {
                throw new BusinessConflictException("No puedes registrar el mismo candidato más de una vez");
            }
            total += item.percentage();
        }

        Map<Long, Candidate> byId = new LinkedHashMap<>();
        for (Candidate candidate : candidates.selectByIds(candidateIds)) {
            byId.put(candidate.id, candidate);
        }

        List<ResolvedResult> resolved = new ArrayList<>(results.size());
        for (PollResultRequest item : results) {
            Candidate candidate = byId.get(item.candidateId());
            if (candidate == null) {
                throw new EntityNotFoundException("El candidato " + item.candidateId() + " no existe");
            }
            if (candidate.election == null || !candidate.election.id.equals(electionId)) {
                throw new IllegalArgumentException(
                        "Todos los candidatos deben pertenecer a la elección seleccionada"
                );
            }
            if (!candidate.active) {
                throw new IllegalArgumentException(
                        "El candidato " + candidate.name + " está inactivo y no puede incluirse"
                );
            }
            resolved.add(new ResolvedResult(candidate, item.percentage()));
        }

        if (total > 100.01) {
            throw new IllegalArgumentException(
                    "La suma de porcentajes no puede superar 100%. Total recibido: " + round(total) + "%"
            );
        }
        if (status == PollStatus.APROBADA && total <= 0) {
            throw new IllegalArgumentException("Una encuesta aprobada debe contener resultados válidos");
        }
        return resolved;
    }

    private List<PollResult> saveResults(Poll poll, List<ResolvedResult> items) {
        List<PollResult> entities = new ArrayList<>(items.size());
        for (ResolvedResult item : items) {
            PollResult result = new PollResult();
            result.poll = poll;
            result.candidate = item.candidate();
            result.percentage = item.percentage();
            entities.add(result);
        }
        return pollResults.saveAll(entities);
    }

    private List<PollResponse> mapPolls(List<Poll> pollList) {
        if (pollList.isEmpty()) {
            return List.of();
        }

        List<Long> pollIds = pollList.stream().map(poll -> poll.id).toList();
        Map<Long, List<PollResult>> byPoll = new LinkedHashMap<>();
        for (PollResult result : pollResults.selectByPollIds(pollIds)) {
            byPoll.computeIfAbsent(result.poll.id, ignored -> new ArrayList<>()).add(result);
        }

        return pollList.stream()
                .map(poll -> toResponse(poll, byPoll.getOrDefault(poll.id, Collections.emptyList())))
                .toList();
    }

    private PollResponse toResponse(Poll poll, List<PollResult> results) {
        List<PollResultResponse> responseResults = results.stream()
                .map(result -> new PollResultResponse(
                        result.id,
                        result.createdAt,
                        result.updatedAt,
                        toCandidateResponse(result.candidate),
                        round(result.percentage)
                ))
                .toList();
        double total = responseResults.stream().mapToDouble(PollResultResponse::percentage).sum();

        return new PollResponse(
                poll.id,
                poll.createdAt,
                poll.updatedAt,
                toElectionResponse(poll.election),
                poll.source,
                poll.date,
                poll.sampleSize,
                round(poll.marginError),
                poll.methodology,
                poll.status.name(),
                round(total),
                responseResults
        );
    }

    private PollCandidateResponse toCandidateResponse(Candidate candidate) {
        PollPartyResponse party = candidate.party == null ? null : new PollPartyResponse(
                candidate.party.id,
                candidate.party.name,
                candidate.party.acronym,
                candidate.party.color
        );
        return new PollCandidateResponse(
                candidate.id,
                candidate.name,
                candidate.active,
                party,
                candidate.election == null ? null : candidate.election.id
        );
    }

    private PollElectionResponse toElectionResponse(Election election) {
        return new PollElectionResponse(
                election.id,
                election.name,
                election.type == null ? null : election.type.name(),
                election.round == null ? null : election.round.name(),
                election.electionDate,
                election.state == null ? null : election.state.name()
        );
    }

    private Poll findPoll(Long id) {
        return polls.selectById(id)
                .orElseThrow(() -> new EntityNotFoundException("La encuesta no existe"));
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
        Set<String> actual = parser.getHeaderMap().keySet();
        List<String> missing = CSV_HEADERS.stream().filter(header -> !actual.contains(header)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "El CSV no contiene las columnas requeridas: " + String.join(", ", missing)
            );
        }
    }

    private Map<ImportKey, ImportAccumulator> parseImports(CSVParser parser) {
        Map<ImportKey, ImportAccumulator> imports = new LinkedHashMap<>();
        for (CSVRecord record : parser) {
            long row = record.getRecordNumber() + 1;
            try {
                Long electionId = parsePositiveLong(record, "electionId", "electionId");
                String source = normalizeRequired(required(record, "source"), "la fuente es obligatoria");
                LocalDate date = parseDate(record, "date");
                Integer sampleSize = parsePositiveInteger(record, "sampleSize", "sampleSize");
                Double marginError = parseDecimal(record, "marginError", "marginError");
                String methodology = normalizeRequired(
                        required(record, "methodology"),
                        "la metodología es obligatoria"
                );
                PollStatus status = parseStatus(required(record, "status"));
                Long candidateId = parsePositiveLong(record, "candidateId", "candidateId");
                Double percentage = parseDecimal(record, "percentage", "percentage");

                ImportKey key = new ImportKey(electionId, source.toLowerCase(Locale.ROOT), date);
                ImportAccumulator accumulator = imports.computeIfAbsent(
                        key,
                        ignored -> new ImportAccumulator(
                                electionId, source, date, sampleSize, marginError, methodology, status
                        )
                );
                accumulator.validateMetadata(sampleSize, marginError, methodology, status, row);
                accumulator.results.add(new PollResultRequest(candidateId, percentage));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Error en la fila " + row + " del CSV: " + readable(exception),
                        exception
                );
            }
        }
        return imports;
    }

    private String required(CSVRecord record, String header) {
        String value = record.get(header);
        return value == null ? "" : value.trim();
    }

    private Long parsePositiveLong(CSVRecord record, String header, String label) {
        String value = required(record, header);
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " debe ser un número entero positivo");
        }
    }

    private Integer parsePositiveInteger(CSVRecord record, String header, String label) {
        String value = required(record, header);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " debe ser un número entero positivo");
        }
    }

    private Double parseDecimal(CSVRecord record, String header, String label) {
        String value = required(record, header);
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " debe ser un número decimal válido");
        }
    }

    private LocalDate parseDate(CSVRecord record, String header) {
        String value = required(record, header);
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("date debe tener el formato AAAA-MM-DD");
        }
    }

    private PollStatus parseStatus(String value) {
        if (value.isBlank()) {
            return PollStatus.PENDIENTE;
        }
        try {
            return PollStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "status debe ser PENDIENTE, APROBADA o RECHAZADA"
            );
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private void validateLength(String value, int maximum, String message) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private String readable(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "valor inválido" : message;
    }

    private long safe(Long value) {
        return value == null ? 0 : value;
    }

    private double round(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private record ResolvedResult(Candidate candidate, Double percentage) {
    }

    private record ImportKey(Long electionId, String source, LocalDate date) {
    }

    private static final class ImportAccumulator {
        private final Long electionId;
        private final String source;
        private final LocalDate date;
        private final Integer sampleSize;
        private final Double marginError;
        private final String methodology;
        private final PollStatus status;
        private final List<PollResultRequest> results = new ArrayList<>();

        private ImportAccumulator(
                Long electionId,
                String source,
                LocalDate date,
                Integer sampleSize,
                Double marginError,
                String methodology,
                PollStatus status
        ) {
            this.electionId = electionId;
            this.source = source;
            this.date = date;
            this.sampleSize = sampleSize;
            this.marginError = marginError;
            this.methodology = methodology;
            this.status = status;
        }

        private void validateMetadata(
                Integer otherSampleSize,
                Double otherMarginError,
                String otherMethodology,
                PollStatus otherStatus,
                long row
        ) {
            if (!sampleSize.equals(otherSampleSize)
                    || Double.compare(marginError, otherMarginError) != 0
                    || !methodology.equals(otherMethodology)
                    || status != otherStatus) {
                throw new IllegalArgumentException(
                        "la fila " + row + " repite la encuesta con datos metodológicos diferentes"
                );
            }
        }

        private PollRequest toRequest() {
            return new PollRequest(
                    electionId,
                    source,
                    date,
                    sampleSize,
                    marginError,
                    methodology,
                    status,
                    List.copyOf(results)
            );
        }
    }
}
