package com.studyhub.aistudyhubbe.service.rag;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

@Component
public class KeywordChunkRanker {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "anh", "cac", "cho", "cua", "duoc", "hay", "hoi", "khong", "mot", "nay", "nhung", "noi",
            "the", "toi", "trong", "va", "ve", "vui", "with", "what", "when", "where", "which", "that",
            "this", "and", "for", "from", "please");

    public List<RankedChunk> rank(String query, List<String> chunks, int topK) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        String normalizedQuery = normalize(query);
        String[] terms = TOKEN_PATTERN.matcher(normalizedQuery)
                .results()
                .map(MatchResult::group)
                .filter(term -> term.length() > 2)
                .filter(term -> !STOP_WORDS.contains(term))
                .distinct()
                .toArray(String[]::new);

        return chunks.stream()
                .map(chunk -> new RankedChunk(chunk, score(chunk, terms)))
                .filter(chunk -> chunk.score() > 0.0)
                .sorted(Comparator.comparingDouble(RankedChunk::score).reversed())
                .limit(Math.max(topK, 1))
                .toList();
    }

    private double score(String chunk, String[] terms) {
        if (terms.length == 0) {
            return 0.0;
        }
        String lower = normalize(chunk);
        double hits = 0.0;
        for (String term : terms) {
            if (lower.contains(term)) {
                hits += countOccurrences(lower, term);
            }
        }
        return hits / terms.length;
    }

    private int countOccurrences(String text, String term) {
        int count = 0;
        int index = text.indexOf(term);
        while (index >= 0) {
            count++;
            index = text.indexOf(term, index + term.length());
        }
        return count;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT).replace('đ', 'd');
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    public record RankedChunk(String content, double score) {
    }
}
