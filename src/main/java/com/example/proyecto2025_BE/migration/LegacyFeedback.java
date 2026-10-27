package com.example.proyecto2025_BE.migration;

import java.time.LocalDateTime;

public record LegacyFeedback(
        String objectId,
        Long userId,
        String questionId,
        String feedbackType,
        String description,
        LocalDateTime createdAt
) {
}
