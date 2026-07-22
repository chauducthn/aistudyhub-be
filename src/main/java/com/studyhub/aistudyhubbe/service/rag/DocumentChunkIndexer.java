package com.studyhub.aistudyhubbe.service.rag;

import com.studyhub.aistudyhubbe.config.RagProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentChunk;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.repository.DocumentChunkRepository;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DocumentChunkIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChunkIndexer.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final TextChunkingService textChunkingService;
    private final RagProperties ragProperties;
    private final GeminiEmbeddingClient geminiEmbeddingClient;
    private final ChunkEmbeddingCodec chunkEmbeddingCodec;

    public DocumentChunkIndexer(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            TextChunkingService textChunkingService,
            RagProperties ragProperties,
            GeminiEmbeddingClient geminiEmbeddingClient,
            ChunkEmbeddingCodec chunkEmbeddingCodec) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.textChunkingService = textChunkingService;
        this.ragProperties = ragProperties;
        this.geminiEmbeddingClient = geminiEmbeddingClient;
        this.chunkEmbeddingCodec = chunkEmbeddingCodec;
    }

    @Transactional
    public void indexDocument(Long documentId) {
        if (!ragProperties.isEnabled()) {
            return;
        }

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            LOGGER.debug("Skipping chunk indexing because document {} no longer exists", documentId);
            return;
        }

        documentChunkRepository.deleteByDocumentId(document.getId());
        if (document.getExtractionStatus() != DocumentExtractionStatus.EXTRACTED) {
            return;
        }
        if (!StringUtils.hasText(document.getExtractedText())) {
            return;
        }

        List<String> chunks = textChunkingService.chunk(document.getExtractedText());
        List<DocumentChunk> entities = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            entities.add(chunk);
        }

        addEmbeddings(document, chunks, entities);
        documentChunkRepository.saveAll(entities);
    }

    private void addEmbeddings(Document document, List<String> chunks, List<DocumentChunk> entities) {
        if (!geminiEmbeddingClient.isConfigured()) {
            LOGGER.debug("Gemini embedding is not configured; indexing document {} with keyword-only chunks", document.getId());
            return;
        }

        try {
            List<float[]> embeddings = geminiEmbeddingClient.embedDocuments(chunks, document.getTitle());
            if (embeddings.size() != entities.size()) {
                LOGGER.warn(
                        "Gemini returned {} embeddings for {} chunks in document {}; using keyword-only chunks",
                        embeddings.size(),
                        entities.size(),
                        document.getId());
                return;
            }
            for (int i = 0; i < entities.size(); i++) {
                String encoded = chunkEmbeddingCodec.encode(embeddings.get(i));
                entities.get(i).setEmbedding(encoded.isBlank() ? null : encoded);
            }
        } catch (RuntimeException ex) {
            LOGGER.warn(
                    "Could not create Gemini embeddings for document {}; chunks remain available for keyword retrieval: {}",
                    document.getId(),
                    ex.getMessage());
        }
    }
}
