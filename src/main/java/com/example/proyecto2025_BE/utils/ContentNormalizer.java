package com.example.proyecto2025_BE.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Normalización compartida por el runtime y el migrador.
 * Un solo criterio para tópicos y para el hash de enunciado evita que el
 * deduplicador y la base usen reglas distintas.
 */
public final class ContentNormalizer {

    public static final int MAX_TOPIC_LENGTH = 120;

    private ContentNormalizer() {
    }

    /**
     * Texto comparable: sin acentos, minúsculas, sin puntuación y con
     * espacios colapsados.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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
     * Clave única de tópico por usuario, truncada al tamaño de la columna.
     */
    public static String topicKey(String raw) {
        String normalized = normalize(raw);
        return normalized.length() > MAX_TOPIC_LENGTH
                ? normalized.substring(0, MAX_TOPIC_LENGTH)
                : normalized;
    }

    /**
     * SHA-256 del enunciado normalizado. Se usa porque la columna es TEXT y
     * no puede formar parte de un índice único directamente.
     */
    public static String enunciadoHash(String enunciado) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalize(enunciado).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
