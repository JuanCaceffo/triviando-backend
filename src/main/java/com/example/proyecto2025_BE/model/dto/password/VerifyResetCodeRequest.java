package com.example.proyecto2025_BE.model.dto.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyResetCodeRequest(
        @NotBlank(message = "Ingresá tu email o nombre de usuario")
        String identifier,

        @NotBlank(message = "Ingresá el código de 6 dígitos")
        @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
        String code
) {
}
