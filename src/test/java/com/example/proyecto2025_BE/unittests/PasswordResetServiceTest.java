package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.PasswordResetToken;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.password.ForgotPasswordResponse;
import com.example.proyecto2025_BE.model.dto.password.VerifyResetCodeResponse;
import com.example.proyecto2025_BE.repository.PasswordResetTokenRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.service.EmailService;
import com.example.proyecto2025_BE.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Password Reset Service Tests")
public class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder encoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User localUser() {
        return User.builder()
                .id(1L)
                .username("juanceto01")
                .email("juan@triviando.com.ar")
                .password("encodedOld")
                .googleAccount(false)
                .build();
    }

    private PasswordResetToken tokenFor(User user) {
        return PasswordResetToken.builder()
                .id(10L)
                .user(user)
                .codeHash(sha256("123456"))
                .attempts(0)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .lastSentAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ===== requestResetCode =====

    @Test
    @DisplayName("Identificador inexistente: respuesta genérica y no se envía email")
    void requestResetCodeUnknownIdentifier() {
        when(userRepository.findByEmail("nadie@nadie.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("nadie@nadie.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("nadie@nadie.com")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = passwordResetService.requestResetCode("nadie@nadie.com");

        assertEquals(ForgotPasswordResponse.GENERIC_MESSAGE, response.message());
        verifyNoInteractions(emailService);
        verifyNoInteractions(tokenRepository);
    }

    @Test
    @DisplayName("Cuenta existente: genera código, guarda token y responde genérico")
    void requestResetCodeExistingUser() {
        User user = localUser();
        when(userRepository.findByEmail("juan@triviando.com.ar")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalse(1L)).thenReturn(Optional.empty());

        ForgotPasswordResponse response = passwordResetService.requestResetCode("juan@triviando.com.ar");

        assertEquals(ForgotPasswordResponse.GENERIC_MESSAGE, response.message());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq("juan@triviando.com.ar"), codeCaptor.capture());
        assertEquals(6, codeCaptor.getValue().length());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();
        assertEquals(sha256(codeCaptor.getValue()), saved.getCodeHash());
        assertFalse(saved.isUsed());
        assertEquals(0, saved.getAttempts());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Cuenta de Google: avisa por email y no envía código, sin filtrar nada en la respuesta")
    void requestResetCodeGoogleAccount() {
        User user = localUser();
        user.setGoogleAccount(true);
        when(userRepository.findByEmail("juan@triviando.com.ar")).thenReturn(Optional.of(user));

        ForgotPasswordResponse response = passwordResetService.requestResetCode("juan@triviando.com.ar");

        assertEquals(ForgotPasswordResponse.GENERIC_MESSAGE, response.message());
        verify(emailService).sendGoogleAccountNotice("juan@triviando.com.ar");
        verify(emailService, never()).sendPasswordResetCode(any(), any());
        verifyNoInteractions(tokenRepository);
    }

    @Test
    @DisplayName("Reenvío dentro del cooldown: responde genérico pero no vuelve a enviar")
    void requestResetCodeWithinCooldown() {
        User user = localUser();
        PasswordResetToken active = tokenFor(user);
        active.setLastSentAt(LocalDateTime.now().minusSeconds(10));

        when(userRepository.findByEmail("juan@triviando.com.ar")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalse(1L)).thenReturn(Optional.of(active));

        ForgotPasswordResponse response = passwordResetService.requestResetCode("juan@triviando.com.ar");

        assertEquals(ForgotPasswordResponse.GENERIC_MESSAGE, response.message());
        verifyNoInteractions(emailService);
        verify(tokenRepository, never()).save(any());
    }

    // ===== verifyResetCode =====

    @Test
    @DisplayName("Código correcto: devuelve un session token con vencimiento")
    void verifyResetCodeCorrect() {
        User user = localUser();
        PasswordResetToken token = tokenFor(user);
        when(userRepository.findByEmail("juan@triviando.com.ar")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalse(1L)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        VerifyResetCodeResponse response = passwordResetService.verifyResetCode("juan@triviando.com.ar", "123456");

        assertNotNull(response.resetToken());
        assertNotNull(token.getResetTokenExpiresAt());
        assertEquals(1, token.getAttempts());
    }

    @Test
    @DisplayName("Código incorrecto: incrementa intentos y devuelve error")
    void verifyResetCodeWrong() {
        User user = localUser();
        PasswordResetToken token = tokenFor(user);
        when(userRepository.findByEmail("juan@triviando.com.ar")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalse(1L)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> passwordResetService.verifyResetCode("juan@triviando.com.ar", "000000"));

        assertTrue(exception.getMessage().contains("incorrecto"));
        assertEquals(1, token.getAttempts());
        assertNull(token.getResetToken());
    }

    @Test
    @DisplayName("Al superar los 5 intentos el token se invalida")
    void verifyResetCodeTooManyAttempts() {
        User user = localUser();
        PasswordResetToken token = tokenFor(user);
        token.setAttempts(PasswordResetService.MAX_ATTEMPTS);

        when(userRepository.findByEmail("juan@triviando.com.ar")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalse(1L)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> passwordResetService.verifyResetCode("juan@triviando.com.ar", "123456"));

        assertTrue(exception.getMessage().contains("Demasiados intentos"));
        assertTrue(token.isUsed());
    }

    @Test
    @DisplayName("Código vencido: devuelve error y invalida el token")
    void verifyResetCodeExpired() {
        User user = localUser();
        PasswordResetToken token = tokenFor(user);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(userRepository.findByEmail("juan@triviando.com.ar")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalse(1L)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> passwordResetService.verifyResetCode("juan@triviando.com.ar", "123456"));

        assertTrue(exception.getMessage().contains("expiró"));
        assertTrue(token.isUsed());
    }

    @Test
    @DisplayName("Identificador sin token activo: error genérico")
    void verifyResetCodeWithoutToken() {
        User user = localUser();
        when(userRepository.findByEmail("juan@triviando.com.ar")).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUserIdAndUsedFalse(1L)).thenReturn(Optional.empty());

        ValidationException exception = assertThrows(ValidationException.class,
                () -> passwordResetService.verifyResetCode("juan@triviando.com.ar", "123456"));

        assertTrue(exception.getMessage().contains("Pedí uno nuevo"));
    }

    // ===== resetPassword =====

    @Test
    @DisplayName("Session token válido: cambia la contraseña y consume el token")
    void resetPasswordSuccess() {
        User user = localUser();
        PasswordResetToken token = tokenFor(user);
        token.setResetToken("session-token");
        token.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(tokenRepository.findFirstByResetTokenAndUsedFalse("session-token")).thenReturn(Optional.of(token));
        when(encoder.encode("NuevaClave123")).thenReturn("encodedNew");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        passwordResetService.resetPassword("session-token", "NuevaClave123");

        assertEquals("encodedNew", user.getPassword());
        assertTrue(token.isUsed());
    }

    @Test
    @DisplayName("Session token inexistente: error de enlace expirado")
    void resetPasswordUnknownToken() {
        when(tokenRepository.findFirstByResetTokenAndUsedFalse("otro")).thenReturn(Optional.empty());

        ValidationException exception = assertThrows(ValidationException.class,
                () -> passwordResetService.resetPassword("otro", "NuevaClave123"));

        assertTrue(exception.getMessage().contains("expiró"));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Session token vencido: se consume y devuelve error")
    void resetPasswordExpiredToken() {
        User user = localUser();
        PasswordResetToken token = tokenFor(user);
        token.setResetToken("session-token");
        token.setResetTokenExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(tokenRepository.findFirstByResetTokenAndUsedFalse("session-token")).thenReturn(Optional.of(token));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> passwordResetService.resetPassword("session-token", "NuevaClave123"));

        assertTrue(exception.getMessage().contains("expiró"));
        assertTrue(token.isUsed());
        verifyNoInteractions(userRepository);
    }
}
