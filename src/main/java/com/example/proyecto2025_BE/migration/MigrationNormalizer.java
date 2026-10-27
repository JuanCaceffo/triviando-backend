package com.example.proyecto2025_BE.migration;

import com.example.proyecto2025_BE.utils.ContentNormalizer;

/**
 * Fachada del migrador sobre {@link ContentNormalizer}. Se mantiene para que
 * el proceso de migración y el runtime compartan exactamente las mismas reglas.
 */
public final class MigrationNormalizer {

    static final int MAX_TOPIC_LENGTH = ContentNormalizer.MAX_TOPIC_LENGTH;

    private MigrationNormalizer() {
    }

    public static String displayName(String raw) {
        return ContentNormalizer.displayName(raw);
    }

    public static String normalizedTopic(String raw) {
        return ContentNormalizer.topicKey(raw);
    }

    public static String enunciadoHash(String enunciado) {
        return ContentNormalizer.enunciadoHash(enunciado);
    }
}
