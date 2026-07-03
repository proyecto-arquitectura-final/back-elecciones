package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Requests.ElectionRequest;
import co.edu.elecciones.dto.Responses.ElectionManagement;
import co.edu.elecciones.dto.Responses.ElectionResponse;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.ElectionManagementService;
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
@RequestMapping("/api/v1/elecciones")
public class EleccionesController {
    private final ElectionManagementService service;
    private final AuditService audit;

    public EleccionesController(ElectionManagementService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<ElectionResponse>> all() {
        return ApiResponse.ok("Elecciones consultadas", service.selectAll());
    }

    @GetMapping("/gestion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<ElectionManagement> management() {
        return ApiResponse.ok("Gestión de elecciones", service.getManagement());
    }

    @GetMapping("/{id}")
    public ApiResponse<ElectionResponse> one(@PathVariable Long id) {
        return ApiResponse.ok("Elección consultada", service.selectById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<ElectionResponse> create(
            @Valid @RequestBody ElectionRequest request,
            HttpServletRequest http
    ) {
        ElectionResponse saved = service.create(request);
        audit.log("CREATE", "Election", saved.id(), "Elección creada: " + saved.name(), http);
        return ApiResponse.ok("Elección creada", saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<ElectionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ElectionRequest request,
            HttpServletRequest http
    ) {
        ElectionResponse saved = service.update(id, request);
        audit.log("UPDATE", "Election", saved.id(), "Elección actualizada: " + saved.name(), http);
        return ApiResponse.ok("Elección actualizada", saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        service.delete(id);
        audit.log("DELETE", "Election", id, "Elección eliminada", http);
        return ApiResponse.ok("Elección eliminada", null);
    }
}
