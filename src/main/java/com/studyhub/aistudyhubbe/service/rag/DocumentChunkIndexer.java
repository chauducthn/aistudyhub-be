package com.studyhub.aistudyhubbe.service.rag;

import com.studyhub.aistudyhubbe.config.RagProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentChunk;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.repository.DocumentChunkRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DocumentChunkIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChunkIndexer.class);

    private final DocumentChunkRepository documentChunkRepository;
    private final TextChunkingService textChunkingService;
    private final GeminiEmbeddingClient geminiEmbeddingClient;
    private final ChunkEmbeddingCodec chunkEmbeddingCodec;

    public DocumentChunkIndexer(
            DocumentChunkRepository documentChunkRepository,
            TextChunkingService textChunkingService,
            GeminiEmbeddingClient geminiEmbeddingClient,
            ChunkEmbeddingCodec chunkEmbeddingCodec) {
        this.documentChunkRepository = documentChunkRepository;
        this.textChunkingService = textChunkingService;
        this.geminiEmbeddingClient = geminiEmbeddingClient;
        this.chunkEmbeddingCodec = chunkEmbeddingCodec;
    }

    @Transactional
    public void indexDocument(Document document) {
        documentChunkRepository.deleteByDocumentId(document.getId());
        if (document.getExtractionStatus() != DocumentExtractionStatus.EXTRACTED) {
            return;
        }
        if (!StringUtils.hasText(document.getExtractedText())) {
            return;
        }

        List<String> chunks = textChunkingService.chunk(document.getExtractedText());
        boolean canEmbed = geminiEmbeddingClient.isConfigured();
        
        List<DocumentChunk> chunkEntities = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunkEntities.add(chunk);
        }

        if (canEmbed && !chunkEntities.isEmpty()) {
            java.util.concurrent.ForkJoinPool customThreadPool = new java.util.concurrent.ForkJoinPool(10);
            try {
                customThreadPool.submit(() -> chunkEntities.parallelStream().forEach(chunk -> {
                    try {
                        float[] vector = geminiEmbeddingClient.embedDocument(chunk.getContent());
                        chunk.setEmbedding(chunkEmbeddingCodec.encode(vector));
                    } catch (RuntimeException ex) {
                        LOGGER.warn("Skipping embedding for document {} chunk {}: {}", document.getId(), chunk.getChunkIndex(), ex.getMessage());
                    }
                })).get();
            } catch (Exception ex) {
                LOGGER.error("Failed to generate embeddings in parallel for document {}: {}", document.getId(), ex.getMessage());
            } finally {
                customThreadPool.shutdown();
            }
        }

        for (DocumentChunk chunk : chunkEntities) {
            documentChunkRepository.save(chunk);
        }
    }
}
