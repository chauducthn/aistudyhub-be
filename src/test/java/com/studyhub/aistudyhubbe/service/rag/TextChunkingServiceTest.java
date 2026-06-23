package com.studyhub.aistudyhubbe.service.rag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studyhub.aistudyhubbe.config.RagProperties;
import org.junit.jupiter.api.Test;

class TextChunkingServiceTest {

    @Test
    void chunksLongTextWithOverlap() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(200);
        properties.setChunkOverlap(40);
        properties.setMaxChunksPerDocument(10);

        TextChunkingService service = new TextChunkingService(properties);
        String text = "alpha ".repeat(80).trim();

        var chunks = service.chunk(text);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.getFirst().contains("alpha"));
    }
}
