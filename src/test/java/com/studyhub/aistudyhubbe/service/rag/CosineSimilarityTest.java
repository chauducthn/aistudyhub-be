package com.studyhub.aistudyhubbe.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CosineSimilarityTest {

    private final CosineSimilarity similarity = new CosineSimilarity();

    @Test
    void scoresParallelAndOrthogonalVectors() {
        assertThat(similarity.score(new float[] {1, 0}, new float[] {2, 0})).isEqualTo(1.0);
        assertThat(similarity.score(new float[] {1, 0}, new float[] {0, 1})).isEqualTo(0.0);
    }

    @Test
    void rejectsDifferentDimensions() {
        assertThat(similarity.score(new float[] {1, 0}, new float[] {1})).isEqualTo(0.0);
    }
}
