package co.edu.elecciones;

import co.edu.elecciones.dto.Requests.ChatRequest;
import co.edu.elecciones.dto.Responses.PublicDashboard;
import co.edu.elecciones.dto.Responses.PublicPredictionDashboard;
import co.edu.elecciones.service.AssistantBusinessContextService;
import co.edu.elecciones.service.AssistantConversationStore;
import co.edu.elecciones.service.GeminiAssistantClient;
import co.edu.elecciones.service.GeminiElectionAssistantService;
import co.edu.elecciones.service.PublicDashboardService;
import co.edu.elecciones.service.PublicPredictionService;
import co.edu.elecciones.service.RuleBasedAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiElectionAssistantServiceTest {

    @Mock private PublicDashboardService dashboardService;
    @Mock private PublicPredictionService predictionService;
    @Mock private RuleBasedAssistantService fallbackService;
    @Mock private AssistantBusinessContextService contextService;
    @Mock private GeminiAssistantClient gemini;
    @Mock private AssistantConversationStore conversationStore;

    private GeminiElectionAssistantService service;
    private PublicDashboard dashboard;
    private PublicPredictionDashboard prediction;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        service = new GeminiElectionAssistantService(
                dashboardService,
                predictionService,
                fallbackService,
                contextService,
                gemini,
                conversationStore
        );
        dashboard = new PublicDashboard(null, List.of(), null, List.of(), List.of());
        prediction = new PublicPredictionDashboard(
                null, List.of(), null, List.of(), List.of(), List.of(), Instant.now());
        sessionId = UUID.randomUUID();

        when(dashboardService.load(1L)).thenReturn(dashboard);
        when(predictionService.load(1L)).thenReturn(prediction);
        when(fallbackService.answer(anyString(), any(), any())).thenReturn(
                new RuleBasedAssistantService.AssistantAnswer(
                        "Respuesta de contingencia", "RESULTS", List.of("public_dashboard")));
        when(conversationStore.resolve(any(), any(), anyString(), anyString())).thenReturn(
                new AssistantConversationStore.SessionHandle(sessionId, null));
        when(conversationStore.recent(sessionId)).thenReturn(List.of());
        when(contextService.build(anyString(), any(), any(), anyList())).thenReturn(
                new AssistantBusinessContextService.BusinessContext(
                        "Contexto electoral controlado", List.of("Resultados oficiales")));
        when(conversationStore.saveMessage(any(), anyString(), anyString(), anyString(), any(),
                anyString(), anyList(), anyBoolean(), any())).thenReturn(null, 77L);
        when(gemini.model()).thenReturn("gemini-2.5-flash");
    }

    @Test
    void usesGeminiWhenConfigured() {
        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.generate(anyString(), anyString())).thenReturn("Respuesta contextual de Gemini");

        var response = service.ask(new ChatRequest("¿Quién lidera?", 1L, null));

        assertEquals("Respuesta contextual de Gemini", response.answer());
        assertEquals("GEMINI", response.provider());
        assertEquals("gemini-2.5-flash", response.model());
        assertEquals(sessionId, response.sessionId());
        assertEquals(77L, response.messageId());
        assertFalse(response.fallback());
        assertTrue(response.toolsUsed().contains("gemini"));
    }

    @Test
    void fallsBackWhenGeminiFails() {
        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.generate(anyString(), anyString())).thenThrow(new IllegalStateException("timeout"));

        var response = service.ask(new ChatRequest("¿Quién lidera?", 1L, null));

        assertEquals("Respuesta de contingencia", response.answer());
        assertEquals("RULES_FALLBACK", response.provider());
        assertTrue(response.fallback());
    }
}
