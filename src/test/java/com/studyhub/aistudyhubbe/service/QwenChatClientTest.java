package com.studyhub.aistudyhubbe.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class QwenChatClientTest {

    private static final Instant NOW = Instant.parse("2026-07-18T06:00:00Z");

    @Test
    void usesRetryAfterSeconds() {
        assertThat(QwenChatClient.parseRetryDelay("7", NOW)).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void supportsRetryAfterHttpDate() {
        String retryAt = ZonedDateTime.ofInstant(NOW.plusSeconds(9), ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        assertThat(QwenChatClient.parseRetryDelay(retryAt, NOW)).isEqualTo(Duration.ofSeconds(9));
    }

    @Test
    void capsLongRetryAfterAndFallsBackForInvalidValues() {
        assertThat(QwenChatClient.parseRetryDelay("60", NOW)).isEqualTo(Duration.ofSeconds(20));
        assertThat(QwenChatClient.parseRetryDelay("invalid", NOW)).isEqualTo(Duration.ofSeconds(2));
        assertThat(QwenChatClient.parseRetryDelay(null, NOW)).isEqualTo(Duration.ofSeconds(2));
    }
}
