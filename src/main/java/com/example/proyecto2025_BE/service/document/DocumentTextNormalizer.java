package com.example.proyecto2025_BE.service.document;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Limpia el texto extraído y lo acota al máximo configurado.
 */
@Component
@RequiredArgsConstructor
public class DocumentTextNormalizer {

    private static final int MIN_TEXT_LENGTH = 50;

    private final DocumentProperties properties;

    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public boolean hasEnoughText(String normalizedText) {
        return normalizedText != null && normalizedText.length() >= MIN_TEXT_LENGTH;
    }

    public String limit(String text) {
        int maxChars = properties.getMaxExtractedChars();
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }
}
