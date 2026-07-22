package com.studyhub.aistudyhubbe.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studyhub.aistudyhubbe.config.RagProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentChunk;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.repository.DocumentChunkRepository;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentChunkIndexerTest {

    @Test
    void storesBatchEmbeddingsWithChunks() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        TextChunkingService chunkingService = mock(TextChunkingService.class);
        GeminiEmbeddingClient embeddingClient = mock(GeminiEmbeddingClient.class);
        RagProperties properties = new RagProperties();
        ChunkEmbeddingCodec codec = new ChunkEmbeddingCodec();

        Document document = extractedDocument();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(document));
        when(chunkingService.chunk(document.getExtractedText())).thenReturn(List.of("first chunk", "second chunk"));
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(embeddingClient.embedDocuments(anyList(), org.mockito.ArgumentMatchers.eq("Course notes")))
                .thenReturn(List.of(new float[] {1, 0}, new float[] {0, 1}));

        DocumentChunkIndexer indexer = new DocumentChunkIndexer(
                documentRepository,
                chunkRepository,
                chunkingService,
                properties,
                embeddingClient,
                codec);
        indexer.indexDocument(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(codec.decode(captor.getValue().getFirst().getEmbedding())).containsExactly(1.0f, 0.0f);
    }

    @Test
    void stillStoresKeywordChunksWhenEmbeddingFails() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        TextChunkingService chunkingService = mock(TextChunkingService.class);
        GeminiEmbeddingClient embeddingClient = mock(GeminiEmbeddingClient.class);
        RagProperties properties = new RagProperties();

        Document document = extractedDocument();
        when(documentRepository.findById(7L)).thenReturn(Optional.of(document));
        when(chunkingService.chunk(document.getExtractedText())).thenReturn(List.of("available chunk"));
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(embeddingClient.embedDocuments(anyList(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("rate limited"));

        DocumentChunkIndexer indexer = new DocumentChunkIndexer(
                documentRepository,
                chunkRepository,
                chunkingService,
                properties,
                embeddingClient,
                new ChunkEmbeddingCodec());
        indexer.indexDocument(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().extracting(DocumentChunk::getEmbedding).isNull();
    }

    private Document extractedDocument() {
        Document document = new Document();
        document.setId(7L);
        document.setTitle("Course notes");
        document.setExtractionStatus(DocumentExtractionStatus.EXTRACTED);
        document.setExtractedText("Readable study content");
        return document;
    }
}
