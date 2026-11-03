package com.example.proyecto2025_BE.views.rooms;

import jakarta.validation.constraints.NotBlank;

public record ReactionRequest(@NotBlank(message = "El emoji es obligatorio") String emoji) {
}
