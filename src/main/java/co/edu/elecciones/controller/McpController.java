package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Requests.McpInvokeRequest;
import co.edu.elecciones.dto.Responses.Tool;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.PartyRepository;
import co.edu.elecciones.service.PollService;
import co.edu.elecciones.service.PredictionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {
    private final OfficialResultRepository results;
    private final PollService polls;
    private final CandidateRepository candidates;
    private final PartyRepository parties;
    private final PredictionService predictions;

    public McpController(OfficialResultRepository results, PollService polls, CandidateRepository candidates,
                         PartyRepository parties, PredictionService predictions) {
        this.results = results;
        this.polls = polls;
        this.candidates = candidates;
        this.parties = parties;
        this.predictions = predictions;
    }

    @GetMapping("/tools")
    public ApiResponse<List<Tool>> tools() {
        return ApiResponse.ok("Herramientas MCP autorizadas", List.of(
                new Tool("consultar_resultados", "Consulta resultados persistidos", List.of("PUBLIC", "ANALISTA", "ADMINISTRADOR")),
                new Tool("consultar_encuestas", "Consulta encuestas registradas", List.of("ANALISTA", "ADMINISTRADOR")),
                new Tool("consultar_candidatos_partidos", "Consulta candidatos y partidos", List.of("PUBLIC", "ANALISTA", "ADMINISTRADOR")),
                new Tool("generar_resumen", "Resumen explicable sin SQL libre", List.of("PUBLIC", "ANALISTA", "ADMINISTRADOR"))
        ));
    }

    @PostMapping("/invoke")
    public ApiResponse<Object> invoke(@RequestBody McpInvokeRequest request) {
        return switch (request.tool()) {
            case "consultar_resultados" -> ApiResponse.ok("OK", results.selectAll());
            case "consultar_encuestas" -> ApiResponse.ok("OK", polls.selectAll());
            case "consultar_candidatos_partidos" -> ApiResponse.ok(
                    "OK",
                    Map.of("candidatos", candidates.selectAll(), "partidos", parties.selectAll())
            );
            case "generar_resumen" -> ApiResponse.ok("OK", predictions.byPartialResults());
            default -> ApiResponse.error("Tool MCP no autorizada");
        };
    }
}
