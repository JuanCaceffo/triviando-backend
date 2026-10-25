package com.example.proyecto2025_BE.service.document;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfDocumentTextExtractor implements DocumentTextExtractor {

    private static final int[] PDF_SIGNATURE = {'%', 'P', 'D', 'F'};

    private final DocumentProperties properties;

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public String extract(byte[] content) {
        if (!DocumentBytes.startsWith(content, PDF_SIGNATURE)) {
            throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
        }

        try (PDDocument document = Loader.loadPDF(content)) {
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
}
