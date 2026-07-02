package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/registraduria")
public class RegistraduriaController {
    private final OfficialResultRepository results;
    private final AuditService audit;

    public RegistraduriaController(OfficialResultRepository results, AuditService audit) {
        this.results = results;
        this.audit = audit;
    }

    @PostMapping("/sincronizar")
    public ApiResponse<Long> sync(HttpServletRequest request) {
        audit.log("SYNC", "Registraduria", null, "Polling/mock ejecutado", request);
        return ApiResponse.ok("Sincronización mock ejecutada", results.selectCount());
    }

    @GetMapping("/estado")
    public ApiResponse<String> status() {
        return ApiResponse.ok("API Registraduría disponible", "MOCK_CONNECTED");
    }
}
