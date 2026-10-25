package com.example.proyecto2025_BE.service.document;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Punto de entrada de la extracción: valida la subida, elige la estrategia
 * según la extensión, normaliza el texto y lo acota.
 */
@Service
@RequiredArgsConstructor
public class DocumentTextExtractionService {

    private final UploadedFileValidator fileValidator;
    private final DocumentTextNormalizer normalizer;
    private final List<DocumentTextExtractor> extractors;

    public String extract(MultipartFile file) {
        String extension = fileValidator.validate(file);
        byte[] content = fileValidator.readBytes(file);

        String rawText = extractors.stream()
                .filter(extractor -> extractor.supports(extension))
                .findFirst()
                .orElseThrow(() -> ValidationException.build(Exceptions.INVALID_FILE_TYPE))
                .extract(content);

        String normalized = normalizer.normalize(rawText);
        if (!normalizer.hasEnoughText(normalized)) {
            throw ValidationException.build(Exceptions.EMPTY_DOCUMENT_TEXT);
        }

        return normalizer.limit(normalized);
    }
}
