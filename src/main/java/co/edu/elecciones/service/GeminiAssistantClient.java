package co.edu.elecciones.service;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GeminiAssistantClient {

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final int maxOutputTokens;
    private volatile Client client;

    public GeminiAssistantClient(
            @Value("${app.gemini.enabled:false}") boolean enabled,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-2.5-flash}") String model,
            @Value("${app.gemini.max-output-tokens:1200}") int maxOutputTokens) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = StringUtils.hasText(model) ? model.trim() : "gemini-2.5-flash";
        this.maxOutputTokens = Math.max(256, Math.min(maxOutputTokens, 4096));
    }

    public String generate(String systemInstruction, String prompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini no está configurado");
        }

        GenerateContentConfig config = GenerateContentConfig.builder()
                .candidateCount(1)
                .maxOutputTokens(maxOutputTokens)
                .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                .build();

        GenerateContentResponse response = client().models.generateContent(model, prompt, config);
        String text = response == null ? null : response.text();
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("Gemini no devolvió contenido utilizable");
        }
        return text.trim();
    }

    public boolean isConfigured() {
        return enabled && StringUtils.hasText(apiKey);
    }

    public String model() {
        return model;
    }

    private Client client() {
        Client existing = client;
        if (existing != null) return existing;
        synchronized (this) {
            if (client == null) {
                client = Client.builder().apiKey(apiKey).build();
            }
            return client;
        }
    }
}
