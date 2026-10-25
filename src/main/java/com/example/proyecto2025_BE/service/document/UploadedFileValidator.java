package com.example.proyecto2025_BE.service.document;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Valida las condiciones generales de la subida (presencia, tamaño y extensión)
 * y expone el contenido en memoria. Las firmas binarias las valida cada
 * extractor, que es quien conoce su formato.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadedFileValidator {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");

    private final DocumentProperties properties;

    /**
     * @return la extensión normalizada del archivo
     */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ValidationException.build(Exceptions.FILE_EMPTY);
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw ValidationException.build(tooLargeMessage());
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
        }
        return extension;
    }

    public byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.warn("No se pudo leer el archivo subido: {}", e.getMessage());
            throw ValidationException.build(Exceptions.INVALID_FILE);
        }
    }

    private String tooLargeMessage() {
        long maxMegabytes = Math.max(1, properties.getMaxFileSizeBytes() / (1024 * 1024));
        return String.format(Exceptions.FILE_TOO_LARGE, maxMegabytes);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
