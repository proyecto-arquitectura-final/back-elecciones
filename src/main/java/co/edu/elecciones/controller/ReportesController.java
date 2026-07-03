package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.ReportFormat;
import co.edu.elecciones.dto.AdminDtos.ReportManagement;
import co.edu.elecciones.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
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

    public ReportesController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/gestion")
    public ApiResponse<ReportManagement> management(
            @RequestParam(required = false) Long electionId
    ) {
        return ApiResponse.ok("Reporte consultado", service.management(electionId));
    }

    @GetMapping("/resultados")
    public ResponseEntity<byte[]> results(
            @RequestParam(required = false) Long electionId,
            @RequestParam(defaultValue = "json") String format,
            HttpServletRequest request
    ) {
        ReportService.GeneratedReport report = service.generate(electionId, format, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.filename() + "\"")
                .contentType(mediaType(report.format()))
                .contentLength(report.content().length)
                .body(report.content());
    }

    private MediaType mediaType(ReportFormat format) {
        return switch (format) {
            case PDF -> MediaType.APPLICATION_PDF;
            case CSV -> MediaType.parseMediaType("text/csv;charset=UTF-8");
            case JSON -> MediaType.APPLICATION_JSON;
        };
    }
}
