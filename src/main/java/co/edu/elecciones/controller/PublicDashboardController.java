package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Responses.PublicDashboard;
import co.edu.elecciones.service.PublicDashboardService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/dashboard")
public class PublicDashboardController {
    private final PublicDashboardService service;

    public PublicDashboardController(PublicDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PublicDashboard> get(@RequestParam(required = false) Long electionId) {
        return ApiResponse.ok("Resultados públicos obtenidos desde la base de datos", service.load(electionId));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long electionId) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resultado-electoral.csv")
                .body(service.exportCsv(electionId));
    }
}
