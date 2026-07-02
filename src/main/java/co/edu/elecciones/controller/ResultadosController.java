package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionResultSummary;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.dto.Requests.ElectionResultSummaryRequest;
import co.edu.elecciones.dto.Requests.OfficialResultRequest;
import co.edu.elecciones.dto.Responses.LiveSummary;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.PredictionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/resultados")
public class ResultadosController {
    private final OfficialResultRepository repository;
    private final ElectionResultSummaryRepository summaries;
    private final ElectionRepository elections;
    private final CandidateRepository candidates;
    private final PredictionService predictions;
    private final AuditService audit;

    public ResultadosController(OfficialResultRepository repository,
                                ElectionResultSummaryRepository summaries,
                                ElectionRepository elections,
                                CandidateRepository candidates,
                                PredictionService predictions,
                                AuditService audit) {
        this.repository = repository;
        this.summaries = summaries;
        this.elections = elections;
        this.candidates = candidates;
        this.predictions = predictions;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<OfficialResult>> all(
            @RequestParam(required = false) Long electionId,
            @RequestParam(required = false) String department
    ) {
        if (electionId != null) {
            return ApiResponse.ok("OK", repository.selectByElectionId(electionId));
        }
        if (department != null && !department.isBlank()) {
            return ApiResponse.ok("OK", repository.selectByDepartment(department));
        }
        return ApiResponse.ok("OK", repository.selectAll());
    }

    @GetMapping("/resumen")
    public ApiResponse<ElectionResultSummary> summary(@RequestParam Long electionId) {
        return ApiResponse.ok(
                "Resumen electoral",
                summaries.selectByElectionId(electionId).orElseThrow(
                        () -> new IllegalArgumentException("No existe resumen para la elección " + electionId)
                )
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<OfficialResult> createOrUpdate(
            @RequestBody OfficialResultRequest request,
            HttpServletRequest http
    ) {
        Election election = elections.selectById(request.electionId())
                .orElseThrow(() -> new IllegalArgumentException("Elección no encontrada"));
        Candidate candidate = candidates.selectById(request.candidateId())
                .orElseThrow(() -> new IllegalArgumentException("Candidato no encontrado"));

        if (candidate.electionType != null && election.type != null && candidate.electionType != election.type) {
            throw new IllegalArgumentException("El candidato no corresponde al tipo de la elección");
        }

        OfficialResult result = repository.selectByNaturalKey(
                request.electionId(),
                request.candidateId(),
                normalize(request.department()),
                normalize(request.municipality())
        ).orElseGet(OfficialResult::new);

        result.election = election;
        result.candidate = candidate;
        result.department = normalize(request.department());
        result.municipality = normalize(request.municipality());
        result.votes = nonNegative(request.votes());
        result.percentage = boundedPercentage(request.percentage());
        result.reportedTables = nonNegative(request.reportedTables());
        result.totalTables = nonNegative(request.totalTables());
        if (result.reportedTables > result.totalTables) {
            throw new IllegalArgumentException("Las mesas reportadas no pueden superar las mesas totales");
        }
        result.participation = boundedPercentage(request.participation());
        result.source = request.source() == null || request.source().isBlank()
                ? "CARGA_MANUAL"
                : request.source().trim();
        result.importedAt = Instant.now();

        boolean update = result.id != null;
        OfficialResult saved = repository.save(result);
        audit.log(update ? "UPDATE" : "CREATE", "OfficialResult", saved.id,
                update ? "Resultado consolidado actualizado" : "Resultado consolidado creado", http);
        return ApiResponse.ok(update ? "Actualizado" : "Creado", saved);
    }

    @PutMapping("/resumen")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<ElectionResultSummary> upsertSummary(
            @RequestBody ElectionResultSummaryRequest request,
            HttpServletRequest http
    ) {
        Election election = elections.selectById(request.electionId())
                .orElseThrow(() -> new IllegalArgumentException("Elección no encontrada"));
        ElectionResultSummary summary = summaries.selectByElectionId(request.electionId())
                .orElseGet(ElectionResultSummary::new);

        summary.election = election;
        summary.eligibleVoters = nonNegative(request.eligibleVoters());
        summary.totalVoters = nonNegative(request.totalVoters());
        summary.validVotes = nonNegative(request.validVotes());
        summary.blankVotes = nonNegative(request.blankVotes());
        summary.nullVotes = nonNegative(request.nullVotes());
        summary.unmarkedVotes = nonNegative(request.unmarkedVotes());
        summary.reportedTables = nonNegative(request.reportedTables());
        summary.totalTables = nonNegative(request.totalTables());
        summary.source = request.source() == null || request.source().isBlank()
                ? "CARGA_MANUAL"
                : request.source().trim();
        summary.importedAt = request.importedAt() == null ? Instant.now() : request.importedAt();

        validateSummary(summary);
        boolean update = summary.id != null;
        ElectionResultSummary saved = summaries.save(summary);
        audit.log(update ? "UPDATE" : "CREATE", "ElectionResultSummary", saved.id,
                update ? "Resumen electoral actualizado" : "Resumen electoral creado", http);
        return ApiResponse.ok(update ? "Resumen actualizado" : "Resumen creado", saved);
    }

    @GetMapping("/live")
    public ApiResponse<LiveSummary> live() {
        long votes = repository.selectTotalVotes();
        double tablePercentage = repository.selectAverageReportedTablePercentage();
        double participation = repository.selectAverageParticipation();
        return ApiResponse.ok(
                "Predicción ≠ resultado oficial",
                new LiveSummary(votes, tablePercentage, participation, predictions.byPartialResults())
        );
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long nonNegative(Long value) {
        return Math.max(0L, value == null ? 0L : value);
    }

    private int nonNegative(Integer value) {
        return Math.max(0, value == null ? 0 : value);
    }

    private double boundedPercentage(Double value) {
        double percentage = value == null ? 0 : value;
        return Math.max(0, Math.min(100, percentage));
    }
}
