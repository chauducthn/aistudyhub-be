package com.studyhub.aistudyhubbe.service.rag;

import com.studyhub.aistudyhubbe.config.RagProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentChunk;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.repository.DocumentChunkRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RagRetrievalService {

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
        if (!ragProperties.isEnabled()) {
            return truncate(document.getExtractedText());
        }

        List<DocumentChunk> storedChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId());
        List<String> selected = storedChunks.isEmpty()
                ? fallbackChunks(document.getExtractedText(), userQuery)
                : rankStoredChunks(storedChunks, userQuery);

        if (selected.isEmpty()) {
            return truncate(document.getExtractedText());
        }
        return joinChunks(selected);
    }

    private List<String> fallbackChunks(String text, String userQuery) {
        List<String> chunks = textChunkingService.chunk(text);
        return keywordChunkRanker.rank(userQuery, chunks, ragProperties.getTopK()).stream()
                .map(KeywordChunkRanker.RankedChunk::content)
                .toList();
    }

    private List<String> rankStoredChunks(List<DocumentChunk> storedChunks, String userQuery) {
        boolean hasEmbeddings = storedChunks.stream().anyMatch(chunk -> StringUtils.hasText(chunk.getEmbedding()));
        if (hasEmbeddings && geminiEmbeddingClient.isConfigured()) {
            float[] queryVector = geminiEmbeddingClient.embed(userQuery);
            return storedChunks.stream()
                    .filter(chunk -> StringUtils.hasText(chunk.getEmbedding()))
                    .map(chunk -> new ScoredChunk(
                            chunk.getContent(),
                            cosineSimilarity.score(queryVector, chunkEmbeddingCodec.decode(chunk.getEmbedding()))))
                    .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                    .limit(ragProperties.getTopK())
                    .map(ScoredChunk::content)
                    .toList();
        }

        List<String> contents = storedChunks.stream().map(DocumentChunk::getContent).toList();
        return keywordChunkRanker.rank(userQuery, contents, ragProperties.getTopK()).stream()
                .map(KeywordChunkRanker.RankedChunk::content)
                .toList();
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

    private String truncate(String text) {
        String normalized = text.trim();
        if (normalized.length() <= ragProperties.getMaxContextChars()) {
            return normalized;
        }
        return normalized.substring(0, ragProperties.getMaxContextChars());
    }

    private record ScoredChunk(String content, double score) {
    }
}
