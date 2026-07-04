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
            When a document is selected, use the provided excerpts as grounding and reason from them to answer the user's specific question.
            Do not summarize the whole document unless the user explicitly asks for a summary.
            Do not repeat the excerpts verbatim. Synthesize, explain implications, and connect ideas when the context supports it.
            If the excerpts are only representative background, infer cautiously and say when the document is insufficient.
            Do not invent facts or claim that a document contains information that is not supported by the provided context.
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

                Answer the user question directly and thoughtfully. Use short paragraphs or bullets when helpful.
                Keep the answer complete; do not stop mid-sentence. If the answer is long, prefer a concise complete summary.
                If the document context is insufficient, say exactly what is missing instead of forcing an answer.
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
