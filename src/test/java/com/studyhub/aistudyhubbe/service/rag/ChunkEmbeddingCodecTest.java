package com.studyhub.aistudyhubbe.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChunkEmbeddingCodecTest {

    private final ChunkEmbeddingCodec codec = new ChunkEmbeddingCodec();

    @Test
    void roundTripsEmbeddingValues() {
        float[] source = {0.125f, -0.25f, 0.75f};

        float[] decoded = codec.decode(codec.encode(source));

        assertThat(decoded).containsExactly(source);
    }

    @Test
    void handlesMissingEmbedding() {
        assertThat(codec.decode(null)).isEmpty();
        assertThat(codec.decode(" ")).isEmpty();
        assertThat(codec.encode(new float[0])).isEmpty();
    }
}
