package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.dto.Requests.OfficialResultRequest;
import co.edu.elecciones.dto.Responses.LiveSummary;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.PredictionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resultados")
public class ResultadosController {
    private final OfficialResultRepository repository;
    private final ElectionRepository elections;
    private final CandidateRepository candidates;
    private final PredictionService predictions;
    private final AuditService audit;

    public ResultadosController(OfficialResultRepository repository, ElectionRepository elections,
                                CandidateRepository candidates, PredictionService predictions, AuditService audit) {
        this.repository = repository;
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

    @PostMapping
    public ApiResponse<OfficialResult> create(@RequestBody OfficialResultRequest request, HttpServletRequest http) {
        OfficialResult result = new OfficialResult();
        result.election = elections.selectById(request.electionId()).orElseThrow();
        result.candidate = candidates.selectById(request.candidateId()).orElseThrow();
        result.department = request.department();
        result.municipality = request.municipality();
        result.votes = request.votes();
        result.percentage = request.percentage();
        result.reportedTables = request.reportedTables();
        result.totalTables = request.totalTables();
        result.participation = request.participation();
        if (request.source() != null) {
            result.source = request.source();
        }
        OfficialResult saved = repository.save(result);
        audit.log("CREATE", "OfficialResult", saved.id, "Resultado importado", http);
        return ApiResponse.ok("Creado", saved);
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
}
