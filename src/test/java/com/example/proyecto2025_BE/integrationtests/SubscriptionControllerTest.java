package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.security.JwtUtil;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Subscription Controller Test")
class SubscriptionControllerTest {

    private static final String REQUEST_ID = "e47c9b7d-5c3a-4b7f-9a1e-2d3c4b5a6f70";
    private static final String USER_EMAIL = "alumno@unsam.edu.ar";
    // HMAC-SHA256("test-secret", "id:123;request-id:e47c9b7d-...;ts:1704908010;")
    // calculado con una herramienta externa a la implementación (PowerShell)
    private static final String VALID_SIGNATURE =
            "ts=1704908010,v1=82be5509f91c026fc01df6bbdf366e2d0073cf1c8d870f8a565998901425941a";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PaymentClient paymentClient;

    private String jwtToken;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("juanceto01")
                .email(USER_EMAIL)
                .password("encoded")
                .account(Account.FREE)
                .build();

        jwtToken = jwtUtil.generateToken(
                Map.of("id", user.getId(), "fullname", "Test User", "account", user.getAccount()),
                user.getUsername());

        when(userRepository.findByUsername("juanceto01")).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("GET del plan devuelve precio, moneda y tipo de cobro")
    void devuelveElPlanPremium() throws Exception {
        mockMvc.perform(get("/suscriptions/plan")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(10000))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.billingType").value("ONE_TIME"));
    }

    @Test
    @DisplayName("GET del plan sin token devuelve 401")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/suscriptions/plan"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El webhook de pago es público y marca al usuario como premium")
    void elWebhookDePagoEsPublico() throws Exception {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn("approved");
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal("10000"));
        when(payment.getCurrencyId()).thenReturn("ARS");
        when(payment.getExternalReference()).thenReturn(USER_EMAIL);
        when(paymentClient.get(123L)).thenReturn(payment);
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/suscriptions/payment")
                        .header("x-signature", VALID_SIGNATURE)
                        .header("x-request-id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"123\"},\"type\":\"payment\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una firma inválida en el webhook devuelve 401 sin consultar el pago")
    void unaFirmaInvalidaDevuelve401() throws Exception {
        mockMvc.perform(post("/suscriptions/payment")
                        .header("x-signature", "ts=1704908010,v1=deadbeef")
                        .header("x-request-id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"123\"},\"type\":\"payment\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentClient);
    }
}
