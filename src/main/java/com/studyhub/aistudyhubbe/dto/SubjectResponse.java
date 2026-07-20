package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.Subject;
import java.time.Instant;

public record SubjectResponse(
        Long id,
        Long userId,
        String name,
        Long documentCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getUser().getId(),
                subject.getName(),
                0L,
                subject.getCreatedAt(),
                subject.getUpdatedAt()
        );
    }

    public static SubjectResponse from(Subject subject, Long documentCount) {
        return new SubjectResponse(
                subject.getId(),
                subject.getUser().getId(),
                subject.getName(),
                documentCount,
                subject.getCreatedAt(),
                subject.getUpdatedAt()
        );
    }
}
