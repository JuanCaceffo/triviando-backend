package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.service.document.DocumentTextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Document Text Normalizer Tests")
class DocumentTextNormalizerTest {

    private DocumentProperties properties;
    private DocumentTextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        properties = new DocumentProperties();
        normalizer = new DocumentTextNormalizer(properties);
    }

    @Test
    @DisplayName("Normaliza saltos de línea, controles y espacios repetidos")
    void normalizaTexto() {
        String raw = "Hola\r\nMundo\r\n\r\n\r\nTexto\tcon   espacios\u0000";

        String normalized = normalizer.normalize(raw);

        assertEquals("Hola\nMundo\n\nTexto con espacios", normalized);
    }

    @Test
    @DisplayName("Un texto nulo se normaliza a vacío")
    void normalizaNulo() {
        assertEquals("", normalizer.normalize(null));
    }

    @Test
    @DisplayName("Exige un mínimo de texto útil")
    void exigeMinimoDeTexto() {
        assertFalse(normalizer.hasEnoughText("corto"));
        assertFalse(normalizer.hasEnoughText(null));
        assertTrue(normalizer.hasEnoughText("x".repeat(50)));
    }

    @Test
    @DisplayName("Acota el texto al máximo configurado")
    void acotaTexto() {
        properties.setMaxExtractedChars(5);

        assertEquals("abcde", normalizer.limit("abcdefgh"));
        assertEquals("abc", normalizer.limit("abc"));
    }
}
