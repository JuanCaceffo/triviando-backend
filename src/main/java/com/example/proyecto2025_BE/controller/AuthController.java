package com.example.proyecto2025_BE.controller;

import com.example.proyecto2025_BE.model.dto.login.GoogleLoginRequest;
import com.example.proyecto2025_BE.model.dto.login.LoginResponseDTO;
import com.example.proyecto2025_BE.model.dto.password.ForgotPasswordRequest;
import com.example.proyecto2025_BE.model.dto.password.ForgotPasswordResponse;
import com.example.proyecto2025_BE.model.dto.password.ResetPasswordRequest;
import com.example.proyecto2025_BE.model.dto.password.VerifyResetCodeRequest;
import com.example.proyecto2025_BE.model.dto.password.VerifyResetCodeResponse;
import com.example.proyecto2025_BE.service.GoogleAuthService;
import com.example.proyecto2025_BE.service.PasswordResetService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleAuthService googleAuthService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/google")
    @SecurityRequirements
    public ResponseEntity<LoginResponseDTO> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(googleAuthService.authenticate(request.credential()));
    }

    /**
     * Pide un código de recuperación. Responde 202 con el mismo mensaje exista o no
     * la cuenta, para no revelar qué emails están registrados.
     */
    @PostMapping("/forgot-password")
    @SecurityRequirements
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.accepted()
                .body(passwordResetService.requestResetCode(request.identifier()));
    }

    @PostMapping("/verify-reset-code")
    @SecurityRequirements
    public ResponseEntity<VerifyResetCodeResponse> verifyResetCode(
            @Valid @RequestBody VerifyResetCodeRequest request) {
        return ResponseEntity.ok(
                passwordResetService.verifyResetCode(request.identifier(), request.code()));
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.resetToken(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
