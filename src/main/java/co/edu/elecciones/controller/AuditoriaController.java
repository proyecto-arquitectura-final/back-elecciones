package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.AdminDtos.AuditManagement;
import co.edu.elecciones.service.AuditManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {
    private final AuditManagementService service;

    public AuditoriaController(AuditManagementService service) {
        this.service = service;
    }

    @GetMapping("/gestion")
    public ApiResponse<AuditManagement> management(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String action,
            @RequestParam(defaultValue = "") String entity,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok("Auditoría consultada", service.getManagement(
                search, action, entity, success, page, size
        ));
    }
}
