package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.ResultValidationStatus;
import co.edu.elecciones.dto.Requests.ElectionResultSummaryRequest;
import co.edu.elecciones.dto.Requests.OfficialResultRequest;
import co.edu.elecciones.dto.Responses.LiveSummary;
import co.edu.elecciones.dto.Responses.OfficialResultResponse;
import co.edu.elecciones.dto.Responses.ResultImportResponse;
import co.edu.elecciones.dto.Responses.ResultManagement;
import co.edu.elecciones.dto.Responses.ResultSummaryResponse;
import co.edu.elecciones.dto.Responses.ResultValidationResponse;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.PredictionService;
import co.edu.elecciones.service.ResultManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resultados")
public class ResultadosController {
    private final ResultManagementService service;
    private final OfficialResultRepository repository;
    private final PredictionService predictions;
    private final AuditService audit;

    public ResultadosController(
            ResultManagementService service,
            OfficialResultRepository repository,
            PredictionService predictions,
            AuditService audit
    ) {
        this.service = service;
        this.repository = repository;
        this.predictions = predictions;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<OfficialResultResponse>> all(
            @RequestParam(required = false) Long electionId,
            @RequestParam(required = false) String department
    ) {
        return ApiResponse.ok(
                "Resultados oficiales consultados",
                service.selectAll(electionId, department)
        );
    }

    @GetMapping("/gestion")
    public ApiResponse<ResultManagement> management(
            @RequestParam(required = false) Long electionId,
            @RequestParam(required = false) ResultValidationStatus status,
            @RequestParam(defaultValue = "") String department,
            @RequestParam(defaultValue = "") String municipality,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(
                "Gestión de resultados consultada",
                service.management(electionId, status, department, municipality, search, page, size)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<OfficialResultResponse> one(@PathVariable Long id) {
        return ApiResponse.ok("Resultado oficial consultado", service.selectById(id));
    }

    @GetMapping("/resumen")
    public ApiResponse<ResultSummaryResponse> summary(@RequestParam Long electionId) {
        return ApiResponse.ok("Consolidado electoral consultado", service.selectSummary(electionId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ApiResponse<OfficialResultResponse>> create(
            @Valid @RequestBody OfficialResultRequest request,
            HttpServletRequest http
    ) {
        OfficialResultResponse saved = service.create(request);
        audit.log("CREATE", "OfficialResult", saved.id(), "Creación de resultado oficial", http);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Resultado oficial creado", saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<OfficialResultResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody OfficialResultRequest request,
            HttpServletRequest http
    ) {
        OfficialResultResponse saved = service.update(id, request);
        audit.log("UPDATE", "OfficialResult", saved.id(), "Actualización de resultado oficial", http);
        return ApiResponse.ok("Resultado oficial actualizado", saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        service.delete(id);
        audit.log("DELETE", "OfficialResult", id, "Eliminación de resultado oficial", http);
        return ApiResponse.ok("Resultado oficial eliminado", null);
    }

    @PutMapping("/resumen")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<ResultSummaryResponse> upsertSummary(
            @Valid @RequestBody ElectionResultSummaryRequest request,
            HttpServletRequest http
    ) {
        ResultSummaryResponse saved = service.upsertSummary(request);
        audit.log(
                "UPSERT",
                "ElectionResultSummary",
                saved.id(),
                "Actualización del consolidado electoral para la elección " + saved.electionId(),
                http
        );
        return ApiResponse.ok("Consolidado electoral guardado", saved);
    }

    @PostMapping("/validar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<ResultValidationResponse> validate(
            @RequestParam Long electionId,
            HttpServletRequest http
    ) {
        ResultValidationResponse validation = service.validateElection(electionId);
        audit.log(
                "VALIDATE",
                "OfficialResult",
                electionId,
                "Validación de resultados: " + validation.validated() + " válidos, "
                        + validation.rejected() + " rechazados",
                http
        );
        return ApiResponse.ok("Validación de resultados completada", validation);
    }

    @PostMapping("/import-csv")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ApiResponse<ResultImportResponse>> importCsv(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest http
    ) {
        ResultImportResponse imported = service.importCsv(file);
        audit.log(
                "IMPORT",
                "OfficialResult",
                null,
                "CSV de resultados: " + imported.created() + " creados, "
                        + imported.updated() + " actualizados",
                http
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Resultados importados", imported));
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
