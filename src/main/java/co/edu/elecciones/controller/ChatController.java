package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Requests.ChatRequest;
import co.edu.elecciones.dto.Responses.ChatResponse;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.PollRepository;
import co.edu.elecciones.service.PredictionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    private final PredictionService predictions;
    private final OfficialResultRepository results;
    private final PollRepository polls;

    public ChatController(PredictionService predictions, OfficialResultRepository results, PollRepository polls) {
        this.predictions = predictions;
        this.results = results;
        this.polls = polls;
    }

    @PostMapping("/ask")
    public ApiResponse<ChatResponse> ask(@RequestBody ChatRequest request) {
        String question = request.question() == null ? "" : request.question().toLowerCase();
        if (question.contains("encuesta")) {
            return ApiResponse.ok("OK", new ChatResponse(
                    "Tengo " + polls.selectCount()
                            + " encuestas registradas. Recuerda: Predicción ≠ resultado oficial.",
                    List.of("consultar_encuestas")
            ));
        }
        if (question.contains("predic")) {
            return ApiResponse.ok("OK", new ChatResponse(
                    "La predicción actual por resultados parciales es: " + predictions.byPartialResults(),
                    List.of("generar_resumen")
            ));
        }
        return ApiResponse.ok("OK", new ChatResponse(
                "Resultados oficiales cargados: " + results.selectCount()
                        + " registros. Puedo consultar resultados, encuestas, candidatos y partidos usando herramientas MCP autorizadas.",
                List.of("consultar_resultados")
        ));
    }
}
