package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.Subject;
import java.time.Instant;

public record SubjectResponse(
        Long id,
        Long userId,
        String name,
        Instant createdAt,
        Instant updatedAt
) {

    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getUser().getId(),
                subject.getName(),
                subject.getCreatedAt(),
                subject.getUpdatedAt()
        );
    }
}
