package com.example.proyecto2025_BE.service.document;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Slf4j
@Component
public class DocxDocumentTextExtractor implements DocumentTextExtractor {

    // Un DOCX es un ZIP: firma PK\x03\x04
    private static final int[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};

    @Override
    public boolean supports(String extension) {
        return "docx".equals(extension);
    }

    @Override
    public String extract(byte[] content) {
        if (!DocumentBytes.startsWith(content, ZIP_SIGNATURE)) {
            throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
        }

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (Exception e) {
            log.warn("No se pudo extraer texto del DOCX: {}", e.getMessage());
            throw ValidationException.build(Exceptions.INVALID_FILE);
        }
    }
}
