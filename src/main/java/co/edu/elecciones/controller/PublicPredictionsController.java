package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Responses.PublicPredictionDashboard;
import co.edu.elecciones.service.PublicPredictionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/predictions")
public class PublicPredictionsController {
    private final PublicPredictionService service;

    public PublicPredictionsController(PublicPredictionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PublicPredictionDashboard> get(@RequestParam(required = false) Long electionId) {
        return ApiResponse.ok(
                "Proyección estadística calculada con datos persistidos. No constituye resultado oficial.",
                service.load(electionId)
        );
    }
}
