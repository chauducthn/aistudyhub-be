package com.studyhub.aistudyhubbe.service.rag;

import com.studyhub.aistudyhubbe.config.RagProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TextChunkingService {

    private final RagProperties ragProperties;

    public TextChunkingService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<String> chunk(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        String normalized = text.trim().replaceAll("\\s+", " ");
        int chunkSize = Math.max(ragProperties.getChunkSize(), 200);
        int overlap = Math.min(ragProperties.getChunkOverlap(), chunkSize / 2);
        int maxChunks = Math.max(ragProperties.getMaxChunksPerDocument(), 1);

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length() && chunks.size() < maxChunks) {
            int end = Math.min(start + chunkSize, normalized.length());
            if (end < normalized.length()) {
                int breakAt = normalized.lastIndexOf(' ', end);
                if (breakAt > start + chunkSize / 2) {
                    end = breakAt;
                }
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isBlank()) {
                chunks.add(piece);
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
