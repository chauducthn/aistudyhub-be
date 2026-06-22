package com.studyhub.aistudyhubbe.service.rag;

import com.studyhub.aistudyhubbe.config.RagProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentChunk;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.repository.DocumentChunkRepository;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RagRetrievalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagRetrievalService.class);
    private static final double MIN_EMBEDDING_SCORE = 0.15D;

    private final RagProperties ragProperties;
    private final DocumentChunkRepository documentChunkRepository;
    private final GeminiEmbeddingClient geminiEmbeddingClient;
    private final ChunkEmbeddingCodec chunkEmbeddingCodec;
    private final CosineSimilarity cosineSimilarity;
    private final KeywordChunkRanker keywordChunkRanker;
    private final TextChunkingService textChunkingService;

    public RagRetrievalService(
            RagProperties ragProperties,
            DocumentChunkRepository documentChunkRepository,
            GeminiEmbeddingClient geminiEmbeddingClient,
            ChunkEmbeddingCodec chunkEmbeddingCodec,
            CosineSimilarity cosineSimilarity,
            KeywordChunkRanker keywordChunkRanker,
            TextChunkingService textChunkingService) {
        this.ragProperties = ragProperties;
        this.documentChunkRepository = documentChunkRepository;
        this.geminiEmbeddingClient = geminiEmbeddingClient;
        this.chunkEmbeddingCodec = chunkEmbeddingCodec;
        this.cosineSimilarity = cosineSimilarity;
        this.keywordChunkRanker = keywordChunkRanker;
        this.textChunkingService = textChunkingService;
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
        boolean hasEmbeddings = storedChunks.stream().anyMatch(chunk -> StringUtils.hasText(chunk.getEmbedding()));
        if (hasEmbeddings && geminiEmbeddingClient.isConfigured()) {
            try {
                float[] queryVector = geminiEmbeddingClient.embedQuestion(userQuery);
                List<String> semanticMatches = storedChunks.stream()
                        .filter(chunk -> StringUtils.hasText(chunk.getEmbedding()))
                        .map(chunk -> new ScoredChunk(
                                chunk.getContent(),
                                cosineSimilarity.score(queryVector, chunkEmbeddingCodec.decode(chunk.getEmbedding()))))
                        .filter(chunk -> chunk.score() >= MIN_EMBEDDING_SCORE)
                        .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                        .limit(ragProperties.getTopK())
                        .map(ScoredChunk::content)
                        .toList();
                if (!semanticMatches.isEmpty()) {
                    return semanticMatches;
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("Embedding retrieval failed. Falling back to keyword retrieval: {}", ex.getMessage());
            }
        }
        return rankTextChunks(storedChunks.stream().map(DocumentChunk::getContent).toList(), userQuery);
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

    private record ScoredChunk(String content, double score) {
    }
}
