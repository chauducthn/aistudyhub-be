package com.studyhub.aistudyhubbe.service.rag;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConversationAwareRetrievalQueryBuilder {

    private static final String USER_PREFIX = "User: ";
    private static final int MAX_RECENT_EXCHANGE_LENGTH = 2_500;
    private static final Pattern FOLLOW_UP_REFERENCE = Pattern.compile(
            "(?U)(câu đó|câu trên|câu\\s+(?:số\\s+)?\\d+|ý trên|phần trên|trả lời trên|vừa rồi|"
                    + "tiếp tục|giải thích thêm|tại sao|vì sao|that question|this question|question\\s+\\d+|"
                    + "previous question|previous answer|the answer above|continue|explain further|why)",
            Pattern.CASE_INSENSITIVE);

    public String build(String prompt, String conversationHistory) {
        String normalizedPrompt = normalize(prompt);
        if (!isReferentialFollowUp(normalizedPrompt) || !StringUtils.hasText(conversationHistory)) {
            return normalizedPrompt;
        }

        String recentExchange = mostRecentExchange(conversationHistory);
        if (!StringUtils.hasText(recentExchange)) {
            return normalizedPrompt;
        }
        return normalizedPrompt + System.lineSeparator() + recentExchange;
    }

    boolean isReferentialFollowUp(String prompt) {
        return StringUtils.hasText(prompt)
                && FOLLOW_UP_REFERENCE.matcher(prompt.toLowerCase(Locale.ROOT)).find();
    }

    private String mostRecentExchange(String conversationHistory) {
        int lastUserIndex = conversationHistory.lastIndexOf(USER_PREFIX);
        if (lastUserIndex < 0) {
            return "";
        }

        String exchange = conversationHistory.substring(lastUserIndex).trim();
        if (exchange.length() <= MAX_RECENT_EXCHANGE_LENGTH) {
            return exchange;
        }
        return exchange.substring(0, MAX_RECENT_EXCHANGE_LENGTH).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
