package com.example.proyecto2025_BE.model.dto.password;

/**
 * Respuesta idéntica exista o no la cuenta (anti-enumeración).
 */
public record ForgotPasswordResponse(
        String message
) {
    public static final String GENERIC_MESSAGE =
            "Si existe una cuenta con esos datos, te enviamos un código a tu correo.";

    public static ForgotPasswordResponse generic() {
        return new ForgotPasswordResponse(GENERIC_MESSAGE);
    }
}
