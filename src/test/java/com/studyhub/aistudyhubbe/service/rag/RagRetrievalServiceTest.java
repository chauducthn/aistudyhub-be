package com.studyhub.aistudyhubbe.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.studyhub.aistudyhubbe.config.RagProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentChunk;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.repository.DocumentChunkRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagRetrievalServiceTest {

    @Test
    void usesSemanticSimilarityForStoredEmbeddings() {
        RagProperties properties = new RagProperties();
        properties.setTopK(1);
        DocumentChunkRepository repository = mock(DocumentChunkRepository.class);
        GeminiEmbeddingClient embeddingClient = mock(GeminiEmbeddingClient.class);
        ChunkEmbeddingCodec codec = new ChunkEmbeddingCodec();
        Document document = extractedDocument();
        DocumentChunk semanticMatch = chunk(document, 0, "Điều kiện hoàn thành chương trình", codec.encode(new float[] {1, 0}));
        DocumentChunk other = chunk(document, 1, "Lịch học hàng tuần", codec.encode(new float[] {0, 1}));

        when(repository.findByDocumentIdOrderByChunkIndexAsc(5L)).thenReturn(List.of(semanticMatch, other));
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(embeddingClient.embedQuestion("Tôi cần làm gì để ra trường?")).thenReturn(new float[] {1, 0});

        RagRetrievalService service = service(properties, repository, embeddingClient, codec);
        String context = service.buildContext(document, "Tôi cần làm gì để ra trường?");

        assertThat(context).contains("Điều kiện hoàn thành chương trình").doesNotContain("Lịch học hàng tuần");
    }

    @Test
    void fallsBackToKeywordRankingWhenGeminiFails() {
        RagProperties properties = new RagProperties();
        properties.setTopK(1);
        DocumentChunkRepository repository = mock(DocumentChunkRepository.class);
        GeminiEmbeddingClient embeddingClient = mock(GeminiEmbeddingClient.class);
        ChunkEmbeddingCodec codec = new ChunkEmbeddingCodec();
        Document document = extractedDocument();
        DocumentChunk target = chunk(document, 0, "Lịch thi cuối kỳ vào ngày 20 tháng 8", codec.encode(new float[] {1, 0}));
        DocumentChunk other = chunk(document, 1, "Quy định sử dụng thư viện", codec.encode(new float[] {0, 1}));

        when(repository.findByDocumentIdOrderByChunkIndexAsc(5L)).thenReturn(List.of(target, other));
        when(embeddingClient.isConfigured()).thenReturn(true);
        when(embeddingClient.embedQuestion("Lịch thi cuối kỳ"))
                .thenThrow(new IllegalStateException("temporary failure"));

        RagRetrievalService service = service(properties, repository, embeddingClient, codec);
        String context = service.buildContext(document, "Lịch thi cuối kỳ");

        assertThat(context).contains("Lịch thi cuối kỳ").doesNotContain("Quy định sử dụng thư viện");
    }

    private RagRetrievalService service(
            RagProperties properties,
            DocumentChunkRepository repository,
            GeminiEmbeddingClient embeddingClient,
            ChunkEmbeddingCodec codec) {
        return new RagRetrievalService(
                properties,
                repository,
                new KeywordChunkRanker(),
                mock(TextChunkingService.class),
                embeddingClient,
                codec,
                new CosineSimilarity());
    }

    private Document extractedDocument() {
        Document document = new Document();
        document.setId(5L);
        document.setExtractionStatus(DocumentExtractionStatus.EXTRACTED);
        document.setExtractedText("Document text");
        return document;
    }

    private DocumentChunk chunk(Document document, int index, String content, String embedding) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        return chunk;
    }
}
