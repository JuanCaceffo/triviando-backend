package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Subscription Controller Test")
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserRepository userRepository;

    private String jwtToken;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .username("juanceto01")
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
}
