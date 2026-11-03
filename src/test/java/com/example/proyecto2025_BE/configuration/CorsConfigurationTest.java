package com.example.proyecto2025_BE.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CORS configuration Test")
class CorsConfigurationTest {

    @Autowired
    @Qualifier("corsConfigurationSource")
    private CorsConfigurationSource source;

    @Test
    @DisplayName("Los orígenes del frontend están permitidos para REST y SSE")
    void allowsFrontendOrigins() {
        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/rooms/ABC123/events"));
        assertNotNull(configuration);
        assertTrue(configuration.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(configuration.getAllowedOrigins().contains("https://triviando-phi.vercel.app"));
        assertTrue(configuration.getAllowedOrigins().contains("https://triviando.com.ar"));
    }
}
