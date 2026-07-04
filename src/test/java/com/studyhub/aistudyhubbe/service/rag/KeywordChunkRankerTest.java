package com.studyhub.aistudyhubbe.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordChunkRankerTest {

    private final KeywordChunkRanker ranker = new KeywordChunkRanker();

    @Test
    void ranksVietnameseQuestionWithDiacritics() {
        List<KeywordChunkRanker.RankedChunk> ranked = ranker.rank(
                "Điều kiện tốt nghiệp là gì?",
                List.of(
                        "Học phí được đóng theo từng học kỳ.",
                        "Điều kiện tốt nghiệp gồm hoàn thành tín chỉ và đạt chuẩn ngoại ngữ.",
                        "Lịch thi được công bố trên cổng sinh viên."),
                2);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.getFirst().content()).contains("Điều kiện tốt nghiệp");
    }

    @Test
    void supportsAccentlessVietnameseQuestion() {
        List<KeywordChunkRanker.RankedChunk> ranked = ranker.rank(
                "dieu kien tot nghiep",
                List.of(
                        "Điều kiện tốt nghiệp gồm hoàn thành tín chỉ.",
                        "Quy định thư viện và tài khoản sinh viên."),
                1);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.getFirst().content()).contains("Điều kiện tốt nghiệp");
    }

    @Test
    void ignoresZeroScoreChunks() {
        List<KeywordChunkRanker.RankedChunk> ranked = ranker.rank(
                "chuẩn đầu ra ngoại ngữ",
                List.of(
                        "Học phí được đóng theo từng học kỳ.",
                        "Lịch thi được công bố trên cổng sinh viên."),
                2);

        assertThat(ranked).isEmpty();
    }
}
