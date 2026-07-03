package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.PollStatus;
import co.edu.elecciones.dto.Requests.PollRequest;
import co.edu.elecciones.dto.Responses.PollImportResponse;
import co.edu.elecciones.dto.Responses.PollManagement;
import co.edu.elecciones.dto.Responses.PollResponse;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.PollService;
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
@RequestMapping("/api/v1/encuestas")
public class EncuestasController {
    private final PollService service;
    private final AuditService audit;

    public EncuestasController(PollService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<PollResponse>> all() {
        return ApiResponse.ok("Encuestas consultadas", service.selectAll());
    }

    @GetMapping("/gestion")
    public ApiResponse<PollManagement> management(
            @RequestParam(required = false) Long electionId,
            @RequestParam(required = false) PollStatus status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(
                "Gestión de encuestas consultada",
                service.management(electionId, status, search, page, size)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<PollResponse> one(@PathVariable Long id) {
        return ApiResponse.ok("Encuesta consultada", service.selectById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ApiResponse<PollResponse>> create(
            @Valid @RequestBody PollRequest request,
            HttpServletRequest http
    ) {
        PollResponse saved = service.create(request);
        audit.log("CREATE", "Poll", saved.id(), "Creación de encuesta", http);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Encuesta creada", saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<PollResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PollRequest request,
            HttpServletRequest http
    ) {
        PollResponse saved = service.update(id, request);
        audit.log("UPDATE", "Poll", saved.id(), "Actualización de encuesta", http);
        return ApiResponse.ok("Encuesta actualizada", saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        service.delete(id);
        audit.log("DELETE", "Poll", id, "Eliminación de encuesta", http);
        return ApiResponse.ok("Encuesta eliminada", null);
    }

    @PostMapping("/import-csv")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ApiResponse<PollImportResponse>> csv(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest http
    ) {
        PollImportResponse imported = service.importCsv(file);
        audit.log(
                "IMPORT",
                "Poll",
                null,
                "CSV de encuestas: " + imported.polls() + " encuestas y " + imported.results() + " resultados",
                http
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Encuestas importadas", imported));
    }
}
