package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Extrae texto plano de los archivos soportados. Todo el procesamiento es en
 * memoria: el archivo original nunca se persiste.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTextExtractor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");
    private static final int MIN_TEXT_LENGTH = 50;

    private final DocumentProperties properties;

    public String extract(MultipartFile file) {
        validateFile(file);

        String extension = extensionOf(file.getOriginalFilename());
        byte[] bytes = readBytes(file);
        validateMagicBytes(extension, bytes);

        String text = switch (extension) {
            case "pdf" -> extractPdf(bytes);
            case "docx" -> extractDocx(bytes);
            case "txt", "md" -> extractPlainText(bytes);
            default -> throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
        };

        String normalized = normalize(text);
        if (normalized.length() < MIN_TEXT_LENGTH) {
            throw ValidationException.build(Exceptions.EMPTY_DOCUMENT_TEXT);
        }

        return normalized.length() > properties.getMaxExtractedChars()
                ? normalized.substring(0, properties.getMaxExtractedChars())
                : normalized;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ValidationException.build(Exceptions.FILE_EMPTY);
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw ValidationException.build(Exceptions.FILE_TOO_LARGE);
        }
        if (!SUPPORTED_EXTENSIONS.contains(extensionOf(file.getOriginalFilename()))) {
            throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.warn("No se pudo leer el archivo subido: {}", e.getMessage());
            throw ValidationException.build(Exceptions.INVALID_FILE);
        }
    }

    /**
     * Validación temprana por firma. El parseo real (PDFBox/POI) es la
     * verificación definitiva, pero esto evita procesar archivos que no
     * coinciden con la extensión declarada.
     */
    private void validateMagicBytes(String extension, byte[] bytes) {
        if (bytes.length < 4) {
            throw ValidationException.build(Exceptions.INVALID_FILE);
        }

        switch (extension) {
            case "pdf" -> {
                if (!(bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F')) {
                    throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
                }
            }
            case "docx" -> {
                // Un DOCX es un ZIP: firma PK\x03\x04
                if (!(bytes[0] == 0x50 && bytes[1] == 0x4B && bytes[2] == 0x03 && bytes[3] == 0x04)) {
                    throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
                }
            }
            case "txt", "md" -> {
                for (int i = 0; i < Math.min(bytes.length, 8192); i++) {
                    if (bytes[i] == 0) {
                        throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
                    }
                }
            }
            default -> throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
        }
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int lastPage = Math.min(document.getNumberOfPages(), properties.getMaxPdfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.max(lastPage, 1));
            return stripper.getText(document);
        } catch (Exception e) {
            log.warn("No se pudo extraer texto del PDF: {}", e.getMessage());
            throw ValidationException.build(Exceptions.INVALID_FILE);
        }
    }

    private String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (Exception e) {
            log.warn("No se pudo extraer texto del DOCX: {}", e.getMessage());
            throw ValidationException.build(Exceptions.INVALID_FILE);
        }
    }

    private String extractPlainText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String normalize(String text) {
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

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
