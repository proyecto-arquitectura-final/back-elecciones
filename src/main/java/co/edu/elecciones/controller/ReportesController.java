package co.edu.elecciones.controller;

import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
public class ReportesController {
    private final ReportService service;
    private final OfficialResultRepository results;

    public ReportesController(ReportService service, OfficialResultRepository results) {
        this.service = service;
        this.results = results;
    }

    @GetMapping("/resultados")
    public ResponseEntity<?> resultados(@RequestParam(defaultValue = "json") String format) {
        if (format.equalsIgnoreCase("csv")) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resultados.csv")
                    .contentType(MediaType.valueOf("text/csv"))
                    .body(service.csv());
        }
        if (format.equalsIgnoreCase("pdf")) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resultados.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(service.pdf());
        }
        return ResponseEntity.ok(results.selectAll());
    }
}
