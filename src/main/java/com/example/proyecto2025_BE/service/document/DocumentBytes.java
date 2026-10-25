package com.example.proyecto2025_BE.service.document;

/**
 * Helpers de bajo nivel para validar firmas binarias de archivos.
 */
final class DocumentBytes {

    private DocumentBytes() {
    }

    static boolean startsWith(byte[] content, int... signature) {
        if (content == null || content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    static boolean containsNulByte(byte[] content, int limit) {
        if (content == null) {
            return false;
        }
        int end = Math.min(content.length, limit);
        for (int i = 0; i < end; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
