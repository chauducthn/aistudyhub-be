package com.studyhub.aistudyhubbe.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationAwareRetrievalQueryBuilderTest {

    private final ConversationAwareRetrievalQueryBuilder builder =
            new ConversationAwareRetrievalQueryBuilder();

    @Test
    void independentQuestionDoesNotIncludeUnrelatedHistory() {
        String history = """
                User: Explain binary search
                Assistant: Binary search repeatedly halves a sorted range.
                """;

        String query = builder.build("Kỳ thi diễn ra khi nào?", history);

        assertThat(query).isEqualTo("Kỳ thi diễn ra khi nào?");
    }

    @Test
    void referentialFollowUpIncludesOnlyTheMostRecentExchange() {
        String history = """
                User: Explain binary search
                Assistant: Binary search repeatedly halves a sorted range.
                User: Nếu nộp ngày 17 tháng 8 thì bị trừ bao nhiêu?
                Assistant: Nhóm bị trừ 20 phần trăm.
                """;

        String query = builder.build("Biến câu đó thành một câu hỏi khó hơn.", history);

        assertThat(query)
                .contains("Biến câu đó thành một câu hỏi khó hơn.")
                .contains("Nếu nộp ngày 17 tháng 8")
                .contains("Nhóm bị trừ 20 phần trăm")
                .doesNotContain("binary search");
    }

    @Test
    void englishFollowUpUsesRecentExchange() {
        String history = """
                User: What is the submission deadline?
                Assistant: The deadline is August 15 at 23:59.
                """;

        String query = builder.build("Make that question harder.", history);

        assertThat(query).contains("What is the submission deadline?");
    }

    @Test
    void numberedQuestionReferenceUsesRecentExchange() {
        String history = """
                User: Tạo 5 câu hỏi trắc nghiệm từ tài liệu.
                Assistant: Câu 1: ... Câu 2: Nội dung nào không nằm trong phạm vi thi?
                """;

        String query = builder.build("Cho tôi đáp án câu 2.", history);

        assertThat(query).contains("Câu 2: Nội dung nào không nằm trong phạm vi thi?");
    }
}
