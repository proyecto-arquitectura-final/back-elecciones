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
        long persisted = results.selectCount();
        audit.log("VERIFY", "OfficialResult", null,
                "Verificación de resultados persistidos: " + persisted, request);
        return ApiResponse.ok("Se verificaron los resultados disponibles en la base de datos", persisted);
    }

    @GetMapping("/estado")
    public ApiResponse<String> status() {
        return ApiResponse.ok("Estado de la fuente de resultados",
                results.selectCount() > 0 ? "DATABASE_WITH_DATA" : "DATABASE_EMPTY");
    }
}
