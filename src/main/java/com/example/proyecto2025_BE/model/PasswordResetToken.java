package com.example.proyecto2025_BE.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token de recuperación de contraseña. Hay como máximo uno activo por usuario:
 * al pedir un código nuevo se elimina el anterior.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 del código de 6 dígitos. Nunca se guarda el código en claro. */
    private String codeHash;

    @Builder.Default
    private int attempts = 0;

    /** Vencimiento del código enviado por email (15 minutos). */
    private LocalDateTime expiresAt;

    /** Último envío, para el cooldown de reenvío (60 segundos). */
    private LocalDateTime lastSentAt;

    /** true cuando el código se usó (o se invalidó por intentos fallidos). */
    @Builder.Default
    private boolean used = false;

    /** Session token de un solo uso que se entrega al validar el código. */
    private String resetToken;

    /** Vencimiento del session token (10 minutos). */
    private LocalDateTime resetTokenExpiresAt;
}
