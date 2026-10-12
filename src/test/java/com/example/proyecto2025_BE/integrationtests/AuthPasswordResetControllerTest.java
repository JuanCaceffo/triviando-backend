package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.PasswordResetToken;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.password.ForgotPasswordResponse;
import com.example.proyecto2025_BE.repository.PasswordResetTokenRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Password Reset Controller Tests")
public class AuthPasswordResetControllerTest {

    private static final String LOCAL_EMAIL = "flow@triviando.com.ar";
    private static final String LOCAL_USERNAME = "flowUser";
    private static final String ORIGINAL_PASSWORD = "Pelele123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private PasswordEncoder encoder;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void beforeEach() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.flush();

        userRepository.save(User.builder()
                .name("Flor")
                .lastName("Flux")
                .username(LOCAL_USERNAME)
                .email(LOCAL_EMAIL)
                .password(encoder.encode(ORIGINAL_PASSWORD))
                .googleAccount(false)
                .build());

        userRepository.save(User.builder()
                .name("Goo")
                .lastName("Gle")
                .username("google_123")
                .email("google@triviando.com.ar")
                .password(encoder.encode("random"))
                .googleAccount(true)
                .build());

        reset(emailService);
    }

    private String forgotPassword(String identifier) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + identifier + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(ForgotPasswordResponse.GENERIC_MESSAGE))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Identificador inexistente responde igual que uno existente (anti-enumeración)")
    void antiEnumeration() throws Exception {
        String existingResponse = forgotPassword(LOCAL_EMAIL);
        String unknownResponse = forgotPassword("noexiste@nadie.com");

        assertEquals(existingResponse, unknownResponse);
    }

    @Test
    @DisplayName("Cuenta existente recibe el código por email, con la misma respuesta genérica")
    void existingUserReceivesCode() throws Exception {
        forgotPassword(LOCAL_EMAIL);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq(LOCAL_EMAIL), captor.capture());
        assertEquals(6, captor.getValue().length());
        verify(emailService, never()).sendGoogleAccountNotice(any());
    }

    @Test
    @DisplayName("Cuenta Google recibe aviso por email en lugar de código, sin filtrar nada")
    void googleAccountReceivesNotice() throws Exception {
        forgotPassword("google@triviando.com.ar");

        verify(emailService).sendGoogleAccountNotice("google@triviando.com.ar");
        verify(emailService, never()).sendPasswordResetCode(any(), any());
        verify(emailService, never()).sendPasswordResetCode(eq(LOCAL_EMAIL), any());
    }

    @Test
    @DisplayName("Segundo pedido dentro del cooldown no reenvía el código")
    void cooldownPreventsResend() throws Exception {
        forgotPassword(LOCAL_EMAIL);
        forgotPassword(LOCAL_EMAIL);

        verify(emailService, times(1)).sendPasswordResetCode(eq(LOCAL_EMAIL), any());
    }

    @Test
    @DisplayName("Flujo completo: código correcto permite cambiar la contraseña y la vieja deja de servir")
    void fullResetFlow() throws Exception {
        forgotPassword(LOCAL_EMAIL);
        var codeCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq(LOCAL_EMAIL), codeCaptor.capture());
        String code = codeCaptor.getValue();

        String verifyResponse = mockMvc.perform(MockMvcRequestBuilders.post("/auth/verify-reset-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + LOCAL_EMAIL + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode verifyJson = mapper.readTree(verifyResponse);
        String resetToken = verifyJson.get("resetToken").asText();
        assertFalse(resetToken.isBlank());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"NuevaClave123\"}"))
                .andExpect(status().isOk());

        // La contraseña vieja ya no entra
        mockMvc.perform(MockMvcRequestBuilders.post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + LOCAL_USERNAME + "\",\"password\":\"" + ORIGINAL_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        // La nueva sí
        mockMvc.perform(MockMvcRequestBuilders.post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + LOCAL_USERNAME + "\",\"password\":\"NuevaClave123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // El session token es de un solo uso: no se puede reutilizar
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"OtraClave456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Código incorrecto devuelve 400 con el mensaje y consume un intento")
    void wrongCode() throws Exception {
        forgotPassword(LOCAL_EMAIL);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/verify-reset-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + LOCAL_EMAIL + "\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("incorrecto")));

        PasswordResetToken token = tokenRepository.findFirstByUserIdAndUsedFalse(
                userRepository.findByEmail(LOCAL_EMAIL).orElseThrow().getId()).orElseThrow();
        assertEquals(1, token.getAttempts());
    }

    @Test
    @DisplayName("Session token inexistente devuelve 400")
    void bogusResetToken() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"no-existe\",\"newPassword\":\"NuevaClave123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("expiró")));
    }

    @Test
    @DisplayName("Código con formato inválido rechaza la request")
    void invalidCodeFormat() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/verify-reset-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + LOCAL_EMAIL + "\",\"code\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", not(emptyString())));
    }
}
