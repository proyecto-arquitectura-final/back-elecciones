package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Requests.ChatFeedbackRequest;
import co.edu.elecciones.dto.Requests.ChatRequest;
import co.edu.elecciones.dto.Responses.ChatFeedbackResponse;
import co.edu.elecciones.dto.Responses.ChatHistory;
import co.edu.elecciones.dto.Responses.ChatResponse;
import co.edu.elecciones.dto.Responses.ChatStatus;
import co.edu.elecciones.service.GeminiElectionAssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final GeminiElectionAssistantService assistantService;

    public ChatController(GeminiElectionAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/ask")
    public ApiResponse<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok("OK", assistantService.ask(request));
    }

    @GetMapping("/status")
    public ApiResponse<ChatStatus> status() {
        return ApiResponse.ok("OK", assistantService.status());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatHistory> history(@PathVariable UUID sessionId) {
        return ApiResponse.ok("OK", assistantService.history(sessionId));
    }

    @PatchMapping("/feedback")
    public ApiResponse<ChatFeedbackResponse> feedback(@Valid @RequestBody ChatFeedbackRequest request) {
        return ApiResponse.ok("OK", assistantService.feedback(request));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> close(@PathVariable UUID sessionId) {
        assistantService.close(sessionId);
        return ApiResponse.ok("Conversación eliminada", (Void) null);
    }
}
