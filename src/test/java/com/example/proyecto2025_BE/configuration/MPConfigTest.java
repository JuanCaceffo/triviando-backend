package com.example.proyecto2025_BE.configuration;

import com.mercadopago.client.preference.PreferenceItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Mercado Pago Config Tests")
class MPConfigTest {

    @Test
    @DisplayName("El plan por defecto cuesta 10000 ARS")
    void planPorDefectoCuestaDiezMilPesos() {
        MPConfig config = new MPConfig();

        PreferenceItemRequest item = config.preferenceItemRequest();

        assertEquals(new BigDecimal("10000"), item.getUnitPrice());
        assertEquals("ARS", item.getCurrencyId());
        assertEquals(1, item.getQuantity());
    }

    @Test
    @DisplayName("El precio del plan se puede configurar")
    void precioDelPlanEsConfigurable() {
        MPConfig config = new MPConfig();
        config.setPlanPrice(new BigDecimal("12345"));

        PreferenceItemRequest item = config.preferenceItemRequest();

        assertEquals(new BigDecimal("12345"), item.getUnitPrice());
    }
}
