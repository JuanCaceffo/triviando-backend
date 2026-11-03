package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Genera códigos cortos, legibles y sin caracteres ambiguos. La unicidad se
 * valida contra la base antes de devolver el código.
 */
@Component
@RequiredArgsConstructor
public class RoomCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 6;
    private static final int MAX_ATTEMPTS = 20;

    private final RoomRepository roomRepository;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = nextCode();
            if (!roomRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ConflictException.build(Exceptions.ROOM_CODE_GENERATION_FAILED);
    }

    String nextCode() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
