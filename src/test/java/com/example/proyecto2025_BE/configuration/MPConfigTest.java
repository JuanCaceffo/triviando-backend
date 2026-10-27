package com.example.proyecto2025_BE.configuration;

import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("Las back URLs no duplican la barra cuando el dominio termina en /")
    void lasBackUrlsNoDuplicanLaBarra() {
        MPConfig config = new MPConfig();
        config.setFeDomain("http://localhost:3000/");

        PreferenceBackUrlsRequest backUrls = config.preferenceBackUrlsRequest();

        assertEquals("http://localhost:3000/payment/success", backUrls.getSuccess());
        assertEquals("http://localhost:3000/payment/failure", backUrls.getFailure());
        assertEquals("http://localhost:3000/payment/pending", backUrls.getPending());
    }

    @Test
    @DisplayName("Solo un dominio HTTPS habilita el auto_return")
    void soloUnDominioHttpsHabilitaAutoReturn() {
        MPConfig config = new MPConfig();

        config.setFeDomain("http://localhost:3000/");
        assertFalse(config.hasSecureBackUrls());

        config.setFeDomain("https://triviando.com.ar/");
        assertTrue(config.hasSecureBackUrls());

        config.setFeDomain(null);
        assertFalse(config.hasSecureBackUrls());
    }
}
