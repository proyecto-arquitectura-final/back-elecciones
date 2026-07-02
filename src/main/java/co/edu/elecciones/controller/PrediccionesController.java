package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Responses.PredictionItem;
import co.edu.elecciones.service.PredictionService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/predicciones")
public class PrediccionesController {
    private final PredictionService service;

    public PrediccionesController(PredictionService s) {
        service = s;
    }

    @GetMapping("/encuestas")
    public ApiResponse<List<PredictionItem>> encuestas() {
        return ApiResponse.ok("Predicción agregada por encuestas", service.byPolls());
    }

    @GetMapping("/resultados-parciales")
    public ApiResponse<List<PredictionItem>> parciales() {
        return ApiResponse.ok("Predicción ≠ resultado oficial", service.byPartialResults());
    }
}
