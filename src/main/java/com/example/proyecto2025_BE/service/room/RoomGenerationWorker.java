package com.example.proyecto2025_BE.service.room;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Worker asíncrono de generación de preguntas de sala. La implementación
 * completa se agrega en el slice de generación.
 */
@Service
@RequiredArgsConstructor
public class RoomGenerationWorker {

    private final RoomGenerationProcessor processor;

    @Async("roomGenerationExecutor")
    public void process(Long generationId) {
        processor.process(generationId);
    }
}
