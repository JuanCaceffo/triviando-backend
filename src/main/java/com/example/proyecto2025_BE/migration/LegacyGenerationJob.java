package com.example.proyecto2025_BE.migration;

import java.time.LocalDateTime;

public record LegacyGenerationJob(
        String objectId,
        Long userId,
        String role,
        String topic,
        String status,
        int progress,
        String extractedText,
        String resultJson,
        String error,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
