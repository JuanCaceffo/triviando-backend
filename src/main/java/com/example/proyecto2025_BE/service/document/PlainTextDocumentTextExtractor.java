package com.example.proyecto2025_BE.service.document;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PlainTextDocumentTextExtractor implements DocumentTextExtractor {

    private static final int BINARY_CHECK_LIMIT = 8192;

    @Override
    public boolean supports(String extension) {
        return "txt".equals(extension) || "md".equals(extension);
    }

    @Override
    public String extract(byte[] content) {
        if (DocumentBytes.containsNulByte(content, BINARY_CHECK_LIMIT)) {
            throw ValidationException.build(Exceptions.INVALID_FILE_TYPE);
        }
        return new String(content, StandardCharsets.UTF_8);
    }
}
