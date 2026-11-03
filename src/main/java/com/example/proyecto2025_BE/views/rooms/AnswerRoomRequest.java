package com.example.proyecto2025_BE.views.rooms;

import com.example.proyecto2025_BE.model.LetterOption;
import jakarta.validation.constraints.NotNull;

public record AnswerRoomRequest(
        @NotNull(message = "La pregunta es obligatoria") Long questionId,
        @NotNull(message = "La opción es obligatoria") LetterOption option) {
}
