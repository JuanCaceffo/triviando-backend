package com.example.proyecto2025_BE.views.rooms;

import com.example.proyecto2025_BE.model.LetterOption;

/**
 * Confirmación de respuesta. Deliberadamente no incluye si acertó ni los
 * puntos: eso recién se revela al cerrar la ronda.
 */
public record AnswerAckResponse(boolean accepted, LetterOption option, long answeredAtEpochMs) {
}
