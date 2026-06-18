package com.studyhub.aistudyhubbe.service.gemini;

import com.studyhub.aistudyhubbe.config.AiProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GeminiChatRequestBuilder {

    private static final String SYSTEM_INSTRUCTION = """
            You are AI Study Hub, an academic AI assistant.
            Answer clearly, accurately, and in the same language as the user's question unless the user asks otherwise.
            Prefer the provided document context when it is available.
            If the context is missing or insufficient, say what is missing and give a useful general study explanation.
            Do not invent citations or claim that a document contains information that is not in the provided context.
            """;

    private final AiProperties aiProperties;

    public GeminiChatRequestBuilder(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public Map<String, Object> build(String prompt, String documentTitle, String documentContext) {
        String userMessage = """
                User question:
                %s

                Selected document:
                %s

                Document context:
                %s

                Please provide a focused study answer. When helpful, include bullet points, definitions, examples, or quiz questions.
                """.formatted(
                prompt,
                StringUtils.hasText(documentTitle) ? documentTitle : "No document selected",
                StringUtils.hasText(documentContext) ? documentContext : "No document context available");

        return Map.of(
                "systemInstruction", textContent(SYSTEM_INSTRUCTION),
                "contents", List.of(textContent(userMessage)),
                "generationConfig", Map.of(
                        "temperature", aiProperties.getGemini().getTemperature(),
                        "maxOutputTokens", aiProperties.getGemini().getMaxOutputTokens()
                )
        );
    }

    public String modelPath() {
        String model = aiProperties.getGemini().getModel();
        if (!StringUtils.hasText(model)) {
            return "models/gemini-2.0-flash";
        }
        String trimmedModel = model.trim();
        return trimmedModel.startsWith("models/") ? trimmedModel : "models/" + trimmedModel;
    }

    private Map<String, Object> textContent(String text) {
        return Map.of("parts", List.of(Map.of("text", text)));
    }
}
