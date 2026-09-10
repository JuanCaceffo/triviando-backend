package com.example.proyecto2025_BE.model.dto.login;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "La credencial de Google es requerida")
        String credential
) {
}
