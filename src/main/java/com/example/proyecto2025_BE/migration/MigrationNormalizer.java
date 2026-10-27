package com.example.proyecto2025_BE.migration;

import com.example.proyecto2025_BE.service.QuestionDeduplicationService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Normalización compartida entre el migrador y el runtime. Reutiliza la
 * normalización del deduplicador para que el hash de {@code enunciado} y la
 * unicidad de tópicos no dependan de dos criterios distintos.
 */
public final class MigrationNormalizer {

    static final int MAX_TOPIC_LENGTH = 120;

    private MigrationNormalizer() {
    }

    /**
     * Nombre visible: conserva mayúsculas y acentos, pero colapsa espacios.
     */
    public static String displayName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    /**
     * Clave de unicidad por usuario: sin acentos, minúsculas, sin puntuación
     * y con espacios colapsados. Truncada al tamaño de la columna.
     */
    public static String normalizedTopic(String raw) {
        String normalized = QuestionDeduplicationService.normalize(raw);
        return normalized.length() > MAX_TOPIC_LENGTH
                ? normalized.substring(0, MAX_TOPIC_LENGTH)
                : normalized;
    }

    /**
     * SHA-256 del enunciado normalizado. Se usa porque la columna es TEXT y no
     * puede formar parte de un índice único directamente.
     */
    public static String enunciadoHash(String enunciado) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(QuestionDeduplicationService.normalize(enunciado)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
