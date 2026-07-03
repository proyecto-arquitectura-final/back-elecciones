package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Requests.CandidateRequest;
import co.edu.elecciones.dto.Responses.CandidateManagement;
import co.edu.elecciones.dto.Responses.CandidateResponse;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.CandidateManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/candidatos")
public class CandidatosController {
    private final CandidateManagementService service;
    private final AuditService audit;

    public CandidatosController(CandidateManagementService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<CandidateResponse>> all() {
        return ApiResponse.ok("Candidatos consultados", service.selectAll());
    }

    @GetMapping("/gestion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<CandidateManagement> management() {
        return ApiResponse.ok("Gestión de candidatos", service.getManagement());
    }

    @GetMapping("/{id}")
    public ApiResponse<CandidateResponse> one(@PathVariable Long id) {
        return ApiResponse.ok("Candidato consultado", service.selectById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<CandidateResponse> create(
            @Valid @RequestBody CandidateRequest request,
            HttpServletRequest http
    ) {
        CandidateResponse saved = service.create(request);
        audit.log("CREATE", "Candidate", saved.id(), "Candidato creado: " + saved.name(), http);
        return ApiResponse.ok("Candidato creado", saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<CandidateResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CandidateRequest request,
            HttpServletRequest http
    ) {
        CandidateResponse saved = service.update(id, request);
        audit.log("UPDATE", "Candidate", saved.id(), "Candidato actualizado: " + saved.name(), http);
        return ApiResponse.ok("Candidato actualizado", saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        service.delete(id);
        audit.log("DELETE", "Candidate", id, "Candidato eliminado", http);
        return ApiResponse.ok("Candidato eliminado", null);
    }
}
