package com.studyhub.aistudyhubbe.service.rag;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class KeywordChunkRanker {

    public List<RankedChunk> rank(String query, List<String> chunks, int topK) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        String[] terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(term -> term.length() > 2)
                .distinct()
                .toArray(String[]::new);

        return chunks.stream()
                .map(chunk -> new RankedChunk(chunk, score(chunk, terms)))
                .sorted(Comparator.comparingDouble(RankedChunk::score).reversed())
                .limit(Math.max(topK, 1))
                .toList();
    }

    private double score(String chunk, String[] terms) {
        if (terms.length == 0) {
            return 0.0;
        }
        String lower = chunk.toLowerCase(Locale.ROOT);
        double hits = 0.0;
        for (String term : terms) {
            if (lower.contains(term)) {
                hits += 1.0;
            }
        }
        return hits / terms.length;
    }

    public record RankedChunk(String content, double score) {
    }
}
