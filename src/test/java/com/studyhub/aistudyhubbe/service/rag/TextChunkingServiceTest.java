package com.studyhub.aistudyhubbe.service.rag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studyhub.aistudyhubbe.config.RagProperties;
import org.junit.jupiter.api.Test;

class TextChunkingServiceTest {

    @Test
    void chunksLongTextWithOverlap() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(40);
        properties.setChunkOverlap(10);
        properties.setMaxChunksPerDocument(10);

        TextChunkingService service = new TextChunkingService(properties);
        String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron";

        var chunks = service.chunk(text);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.getFirst().contains("alpha"));
    }
}
