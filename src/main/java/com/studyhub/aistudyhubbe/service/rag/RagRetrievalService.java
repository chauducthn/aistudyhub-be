package com.studyhub.aistudyhubbe.service.rag;

import com.studyhub.aistudyhubbe.config.RagProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentChunk;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.repository.DocumentChunkRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RagRetrievalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagRetrievalService.class);

    private final RagProperties ragProperties;
    private final DocumentChunkRepository documentChunkRepository;
    private final KeywordChunkRanker keywordChunkRanker;
    private final TextChunkingService textChunkingService;
    private final GeminiEmbeddingClient geminiEmbeddingClient;
    private final ChunkEmbeddingCodec chunkEmbeddingCodec;
    private final CosineSimilarity cosineSimilarity;

    public RagRetrievalService(
            RagProperties ragProperties,
            DocumentChunkRepository documentChunkRepository,
            KeywordChunkRanker keywordChunkRanker,
            TextChunkingService textChunkingService,
            GeminiEmbeddingClient geminiEmbeddingClient,
            ChunkEmbeddingCodec chunkEmbeddingCodec,
            CosineSimilarity cosineSimilarity) {
        this.ragProperties = ragProperties;
        this.documentChunkRepository = documentChunkRepository;
        this.keywordChunkRanker = keywordChunkRanker;
        this.textChunkingService = textChunkingService;
        this.geminiEmbeddingClient = geminiEmbeddingClient;
        this.chunkEmbeddingCodec = chunkEmbeddingCodec;
        this.cosineSimilarity = cosineSimilarity;
    }

    @Transactional(readOnly = true)
    public String buildContext(Document document, String userQuery) {
        if (document == null) {
            return "No specific document was selected.";
        }
        if (document.getExtractionStatus() != DocumentExtractionStatus.EXTRACTED) {
            return "Text is not available yet for this document. Extraction status: " + document.getExtractionStatus();
        }
        if (!StringUtils.hasText(document.getExtractedText())) {
            return "No readable text was extracted from this document.";
        }

        List<DocumentChunk> storedChunks = ragProperties.isEnabled()
                ? documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId())
                : List.of();
        List<String> availableChunks = storedChunks.isEmpty()
                ? textChunkingService.chunk(document.getExtractedText())
                : storedChunks.stream().map(DocumentChunk::getContent).toList();
        List<String> selected = storedChunks.isEmpty()
                ? rankTextChunks(availableChunks, userQuery)
                : rankStoredChunks(storedChunks, userQuery);

        if (!selected.isEmpty()) {
            return """
                    Relevant excerpts:

                    %s
                    """.formatted(joinChunks(selected));
        }
        return buildOverviewContext(availableChunks);
    }

    private List<String> rankTextChunks(List<String> chunks, String userQuery) {
        return keywordChunkRanker.rank(userQuery, chunks, ragProperties.getTopK()).stream()
                .map(KeywordChunkRanker.RankedChunk::content)
                .toList();
    }

    private List<String> rankStoredChunks(List<DocumentChunk> storedChunks, String userQuery) {
        List<String> contents = storedChunks.stream().map(DocumentChunk::getContent).toList();
        boolean hasStoredEmbeddings = storedChunks.stream()
                .anyMatch(chunk -> StringUtils.hasText(chunk.getEmbedding()));
        if (!geminiEmbeddingClient.isConfigured() || !hasStoredEmbeddings) {
            return rankTextChunks(contents, userQuery);
        }

        try {
            float[] queryEmbedding = geminiEmbeddingClient.embedQuestion(userQuery);
            if (queryEmbedding.length == 0) {
                return rankTextChunks(contents, userQuery);
            }
            return hybridRank(storedChunks, contents, userQuery, queryEmbedding);
        } catch (RuntimeException ex) {
            LOGGER.warn("Gemini query embedding failed; falling back to keyword retrieval: {}", ex.getMessage());
            return rankTextChunks(contents, userQuery);
        }
    }

    private List<String> hybridRank(
            List<DocumentChunk> storedChunks,
            List<String> contents,
            String userQuery,
            float[] queryEmbedding) {
        List<KeywordChunkRanker.RankedChunk> keywordRanked = keywordChunkRanker.rank(
                userQuery,
                contents,
                Math.max(contents.size(), 1));
        Map<String, Double> keywordScores = keywordRanked.stream().collect(Collectors.toMap(
                KeywordChunkRanker.RankedChunk::content,
                KeywordChunkRanker.RankedChunk::score,
                Math::max));
        double maxKeywordScore = keywordScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        double semanticWeight = Math.max(ragProperties.getSemanticWeight(), 0.0);
        double keywordWeight = Math.max(ragProperties.getKeywordWeight(), 0.0);
        double totalWeight = semanticWeight + keywordWeight;
        if (totalWeight == 0.0) {
            semanticWeight = 0.75;
            keywordWeight = 0.25;
            totalWeight = 1.0;
        }
        final double normalizedSemanticWeight = semanticWeight / totalWeight;
        final double normalizedKeywordWeight = keywordWeight / totalWeight;
        final double keywordDenominator = maxKeywordScore;

        return storedChunks.stream()
                .map(chunk -> scoreChunk(
                        chunk,
                        queryEmbedding,
                        keywordScores.getOrDefault(chunk.getContent(), 0.0),
                        keywordDenominator,
                        normalizedSemanticWeight,
                        normalizedKeywordWeight))
                .filter(HybridChunk::relevant)
                .sorted(Comparator.comparingDouble(HybridChunk::score)
                        .reversed()
                        .thenComparingInt(HybridChunk::chunkIndex))
                .limit(Math.max(ragProperties.getTopK(), 1))
                .map(HybridChunk::content)
                .toList();
    }

    private HybridChunk scoreChunk(
            DocumentChunk chunk,
            float[] queryEmbedding,
            double keywordScore,
            double maxKeywordScore,
            double semanticWeight,
            double keywordWeight) {
        double semanticScore = 0.0;
        if (StringUtils.hasText(chunk.getEmbedding())) {
            try {
                semanticScore = cosineSimilarity.score(queryEmbedding, chunkEmbeddingCodec.decode(chunk.getEmbedding()));
            } catch (NumberFormatException ex) {
                LOGGER.debug("Ignoring malformed embedding for document chunk {}", chunk.getId());
            }
        }

        double normalizedKeywordScore = maxKeywordScore > 0.0 ? keywordScore / maxKeywordScore : 0.0;
        double combinedScore = semanticWeight * Math.max(semanticScore, 0.0)
                + keywordWeight * normalizedKeywordScore;
        boolean relevant = semanticScore >= ragProperties.getMinSemanticScore() || normalizedKeywordScore > 0.0;
        return new HybridChunk(chunk.getContent(), chunk.getChunkIndex(), combinedScore, relevant);
    }

    private String buildOverviewContext(List<String> chunks) {
        List<String> overview = representativeChunks(chunks);
        if (overview.isEmpty()) {
            return "No relevant document context was found for this question.";
        }
        return """
                No exact excerpt matched the user's wording. Use these representative document excerpts only as background,
                then answer cautiously. If the answer cannot be inferred from them, say the document is insufficient.

                Representative excerpts:

                %s
                """.formatted(joinChunks(overview));
    }

    private String joinChunks(List<String> chunks) {
        StringBuilder builder = new StringBuilder();
        for (String chunk : chunks) {
            if (builder.length() + chunk.length() + 2 > ragProperties.getMaxContextChars()) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
            }
            builder.append(chunk.trim());
        }
        return builder.isEmpty() ? "No relevant document context was found." : builder.toString();
    }

    private List<String> representativeChunks(List<String> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        int topK = Math.max(ragProperties.getTopK(), 1);
        if (chunks.size() <= topK) {
            return chunks;
        }
        if (topK == 1) {
            return List.of(chunks.getFirst());
        }

        return java.util.stream.IntStream.range(0, topK)
                .map(i -> Math.min((int) Math.round(i * (chunks.size() - 1D) / (topK - 1D)), chunks.size() - 1))
                .distinct()
                .mapToObj(chunks::get)
                .toList();
    }

    private record HybridChunk(String content, int chunkIndex, double score, boolean relevant) {
    }
}
