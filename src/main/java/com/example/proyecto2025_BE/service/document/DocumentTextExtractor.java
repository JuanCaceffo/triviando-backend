package com.example.proyecto2025_BE.service.document;

/**
 * Estrategia de extracción de texto para un formato de archivo.
 * Cada implementación conoce su extensión, su firma binaria y su parser.
 */
public interface DocumentTextExtractor {

    boolean supports(String extension);

    String extract(byte[] content);
}
