package com.example.proyecto2025_BE.views.rooms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank(message = "El nombre del tema es obligatorio")
        @Size(max = 120, message = "El nombre del tema no puede superar los 120 caracteres")
        String topic) {
}
