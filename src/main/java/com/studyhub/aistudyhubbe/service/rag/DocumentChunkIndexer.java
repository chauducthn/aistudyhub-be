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
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            if (canEmbed) {
                try {
                    chunk.setEmbedding(chunkEmbeddingCodec.encode(geminiEmbeddingClient.embedDocument(chunks.get(i))));
                } catch (RuntimeException ex) {
                    LOGGER.warn("Skipping embedding for document {} chunk {}: {}", document.getId(), i, ex.getMessage());
                }
            }
            documentChunkRepository.save(chunk);
        }
    }
}
