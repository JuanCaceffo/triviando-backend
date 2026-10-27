package com.example.proyecto2025_BE.migration;

import java.time.LocalDateTime;

/**
 * Pregunta tal como existe en la colección {@code preguntas} de MongoDB.
 * Es un snapshot inmutable: el migrador no usa las entidades Mongo del runtime.
 */
public record LegacyQuestion(
        String objectId,
        Long userId,
        String topic,
        String emoji,
        String enunciado,
        java.util.List<LegacyOption> options,
        String correctLetter,
        String explicacion,
        String difficulty,
        LocalDateTime createdAt
) {
}
