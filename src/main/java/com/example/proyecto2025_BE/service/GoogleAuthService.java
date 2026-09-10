package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.login.LoginResponseDTO;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.security.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${google.client-id:}")
    private String googleClientId;

    @Transactional
    public LoginResponseDTO authenticate(String credential) {
        GoogleIdToken.Payload payload = verifyCredential(credential);
        String email = payload.getEmail();

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createUser(payload));

        String token = jwtUtil.generateToken(Map.of(
                "id", user.getId(),
                "fullname", user.getFullName(),
                "account", user.getAccount()
        ), user.getUsername());

        return LoginResponseDTO.builder().token(token).build();
    }

    private GoogleIdToken.Payload verifyCredential(String credential) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalStateException("La configuración google.client-id no está definida");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance()
            ).setAudience(Collections.singletonList(googleClientId)).build();

            GoogleIdToken token = verifier.verify(credential);
            if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
                throw new BadCredentialsException("La credencial de Google no es válida");
            }
            return token.getPayload();
        } catch (GeneralSecurityException | IOException exception) {
            throw new BadCredentialsException("No se pudo validar la credencial de Google", exception);
        }
    }

    private User createUser(GoogleIdToken.Payload payload) {
        String fullName = valueOrDefault((String) payload.get("name"), payload.getEmail());
        String[] nameParts = fullName.trim().split("\\s+", 2);
        String name = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        User user = User.builder()
                .name(name)
                .lastName(lastName)
                .email(payload.getEmail())
                .username("google_" + payload.getSubject())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .build();

        return userRepository.save(user);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
