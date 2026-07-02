package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.AuditEvent;
import co.edu.elecciones.repository.AuditEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {
    private final AuditEventRepository repository;

    public AuditoriaController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<AuditEvent>> all() {
        return ApiResponse.ok("OK", repository.selectAll());
    }
}
