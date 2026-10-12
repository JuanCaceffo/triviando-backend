package com.example.proyecto2025_BE.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.PasswordResetToken;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.password.ForgotPasswordResponse;
import com.example.proyecto2025_BE.model.dto.password.VerifyResetCodeResponse;
import com.example.proyecto2025_BE.repository.PasswordResetTokenRepository;
import com.example.proyecto2025_BE.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Flujo de recuperación de contraseña:
 * 1) requestResetCode  -> envía un código de 6 dígitos por email (respuesta genérica siempre)
 * 2) verifyResetCode   -> valida el código y devuelve un session token de un solo uso
 * 3) resetPassword     -> cambia la contraseña usando el session token
 *
 * noRollbackFor ValidationException: los intentos fallidos y tokens invalidados deben
 * persistirse aunque el método termine lanzando el error que el usuario ve.
 */
@Service
@Transactional(noRollbackFor = ValidationException.class)
@RequiredArgsConstructor
public class PasswordResetService {

    public static final int CODE_LENGTH = 6;
    public static final int CODE_TTL_MINUTES = 15;
    public static final int RESEND_COOLDOWN_SECONDS = 60;
    public static final int MAX_ATTEMPTS = 5;
    public static final int RESET_TOKEN_TTL_MINUTES = 10;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder encoder;

    private final SecureRandom random = new SecureRandom();

    /**
     * Siempre responde lo mismo, exista o no la cuenta (anti-enumeración).
     * Las cuentas de Google no reciben código: se les avisa por email que
     * deben iniciar sesión con Google, sin filtrar eso en la respuesta.
     */
    public ForgotPasswordResponse requestResetCode(String identifier) {
        ForgotPasswordResponse generic = ForgotPasswordResponse.generic();
        Optional<User> userOpt = findUserByIdentifier(identifier);
        if (userOpt.isEmpty()) {
            return generic;
        }

        User user = userOpt.get();
        if (user.isGoogleAccount()) {
            emailService.sendGoogleAccountNotice(user.getEmail());
            return generic;
        }

        Optional<PasswordResetToken> existing =
                tokenRepository.findFirstByUserIdAndUsedFalse(user.getId());

        // Cooldown de reenvío: responde igual pero no vuelve a enviar.
        if (existing.isPresent()
                && existing.get().getLastSentAt() != null
                && existing.get().getLastSentAt().isAfter(LocalDateTime.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            return generic;
        }

        String code = generateCode();
        // Un solo token por usuario: limpiamos los anteriores (usados o vencidos).
        tokenRepository.deleteAllForUser(user.getId());

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(hash(code))
                .attempts(0)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES))
                .lastSentAt(LocalDateTime.now())
                .build();
        tokenRepository.save(token);

        emailService.sendPasswordResetCode(user.getEmail(), code);
        return generic;
    }

    public VerifyResetCodeResponse verifyResetCode(String identifier, String code) {
        User user = findUserByIdentifier(identifier).orElseThrow(PasswordResetService::invalidCode);

        PasswordResetToken token = tokenRepository
                .findFirstByUserIdAndUsedFalse(user.getId())
                .orElseThrow(PasswordResetService::invalidCode);

        if (token.isUsed() || token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsed(true);
            tokenRepository.save(token);
            throw ValidationException.build("El código expiró. Pedí uno nuevo.");
        }

        if (token.getAttempts() >= MAX_ATTEMPTS) {
            token.setUsed(true);
            tokenRepository.save(token);
            throw ValidationException.build("Demasiados intentos. Pedí un código nuevo.");
        }

        token.setAttempts(token.getAttempts() + 1);

        if (!codeMatches(code, token.getCodeHash())) {
            tokenRepository.save(token);
            int remaining = MAX_ATTEMPTS - token.getAttempts();
            throw ValidationException.build(remaining > 0
                    ? "El código es incorrecto. Te quedan " + remaining + " intentos."
                    : "Demasiados intentos. Pedí un código nuevo.");
        }

        token.setResetToken(UUID.randomUUID().toString());
        token.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
        tokenRepository.save(token);

        return new VerifyResetCodeResponse(token.getResetToken());
    }

    public void resetPassword(String resetToken, String newPassword) {
        PasswordResetToken token = tokenRepository
                .findFirstByResetTokenAndUsedFalse(resetToken)
                .orElseThrow(() -> ValidationException.build(
                        "El enlace de recuperación expiró. Pedí uno nuevo."));

        if (token.getResetTokenExpiresAt() == null
                || token.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsed(true);
            tokenRepository.save(token);
            throw ValidationException.build("El enlace de recuperación expiró. Pedí uno nuevo.");
        }

        User user = token.getUser();
        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String value = identifier.trim();
        return userRepository.findByEmail(value)
                .or(() -> userRepository.findByUsername(value))
                .or(() -> userRepository.findByEmailIgnoreCase(value));
    }

    private String generateCode() {
        int max = (int) Math.pow(10, CODE_LENGTH);
        int number = random.nextInt(max);
        return String.format("%0" + CODE_LENGTH + "d", number);
    }

    private String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo hashear el código", exception);
        }
    }

    private boolean codeMatches(String code, String codeHash) {
        if (code == null || codeHash == null) {
            return false;
        }
        byte[] actual = hash(code).getBytes(StandardCharsets.UTF_8);
        byte[] expected = codeHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static ValidationException invalidCode() {
        return ValidationException.build("El código es incorrecto o expiró. Pedí uno nuevo.");
    }
}
