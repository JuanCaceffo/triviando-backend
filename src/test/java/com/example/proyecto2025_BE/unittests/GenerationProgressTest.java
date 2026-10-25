package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.model.GenerationProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Generation Progress Tests")
class GenerationProgressTest {

    @Test
    @DisplayName("El inicio del resumen arranca en el piso del tramo")
    void inicioDelResumen() {
        assertEquals(10, GenerationProgress.fromSummaryPercentage(-5));
        assertEquals(10, GenerationProgress.fromSummaryPercentage(0));
    }

    @Test
    @DisplayName("El fin del resumen llega al techo del tramo")
    void finDelResumen() {
        assertEquals(60, GenerationProgress.fromSummaryPercentage(100));
        assertEquals(60, GenerationProgress.fromSummaryPercentage(150));
    }

    @Test
    @DisplayName("El progreso intermedio se interpola")
    void progresoIntermedio() {
        assertEquals(35, GenerationProgress.fromSummaryPercentage(50));
    }
}
