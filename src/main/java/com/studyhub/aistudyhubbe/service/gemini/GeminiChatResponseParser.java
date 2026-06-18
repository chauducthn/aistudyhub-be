package com.studyhub.aistudyhubbe.service.gemini;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GeminiChatResponseParser {

    public String extractText(GeminiGenerateResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return "";
        }

        Candidate firstCandidate = response.candidates().getFirst();
        if (firstCandidate.content() == null || firstCandidate.content().parts() == null) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (Part part : firstCandidate.content().parts()) {
            if (StringUtils.hasText(part.text())) {
                if (!text.isEmpty()) {
                    text.append(System.lineSeparator());
                }
                text.append(part.text().trim());
            }
        }
        return text.toString();
    }

    public record GeminiGenerateResponse(List<Candidate> candidates) {
    }

    public record Candidate(Content content, String finishReason) {
    }

    public record Content(List<Part> parts, String role) {
    }

    public record Part(String text) {
    }
}
