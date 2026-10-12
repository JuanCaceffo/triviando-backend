package com.example.proyecto2025_BE.model.dto.password;

import jakarta.validation.constraints.NotBlank;

/**
 * Identificador del usuario: email o username.
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "Ingresá tu email o nombre de usuario")
        String identifier
) {
}
