package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.service.MercadoPagoSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Mercado Pago Webhook Signature Tests")
class MercadoPagoSignatureTest {

    private static final String SECRET = "clave-secreta-webhook";
    private static final String REQUEST_ID = "e47c9b7d-5c3a-4b7f-9a1e-2d3c4b5a6f70";
    private static final String DATA_ID = "123456";
    // HMAC-SHA256("clave-secreta-webhook", "id:123456;request-id:e47c9b7d-5c3a-4b7f-9a1e-2d3c4b5a6f70;ts:1704908010;")
    // calculado con una herramienta externa a la implementación (PowerShell)
    private static final String VALID_SIGNATURE =
            "ts=1704908010,v1=d32a648878f3625981ced0a69d883ccde98371f8e6dc8251a4086a5e8b473d24";

    @Test
    @DisplayName("Acepta una firma calculada con el secreto correcto")
    void aceptaFirmaValida() {
        assertTrue(MercadoPagoSignature.isValid(DATA_ID, REQUEST_ID, VALID_SIGNATURE, SECRET));
    }

    @Test
    @DisplayName("Rechaza una firma con hash distinto")
    void rechazaFirmaConHashDistinto() {
        String tampered = "ts=1704908010,v1=0000000000000000000000000000000000000000000000000000000000000000";

        assertFalse(MercadoPagoSignature.isValid(DATA_ID, REQUEST_ID, tampered, SECRET));
    }

    @Test
    @DisplayName("Rechaza una firma calculada con otro secreto")
    void rechazaFirmaConOtroSecreto() {
        assertFalse(MercadoPagoSignature.isValid(DATA_ID, REQUEST_ID, VALID_SIGNATURE, "otro-secreto"));
    }

    @Test
    @DisplayName("Rechaza la notificación si falta la firma o el request id")
    void rechazaSiFaltanDatos() {
        assertFalse(MercadoPagoSignature.isValid(DATA_ID, REQUEST_ID, null, SECRET));
        assertFalse(MercadoPagoSignature.isValid(DATA_ID, REQUEST_ID, "", SECRET));
        assertFalse(MercadoPagoSignature.isValid(DATA_ID, null, VALID_SIGNATURE, SECRET));
        assertFalse(MercadoPagoSignature.isValid(null, REQUEST_ID, VALID_SIGNATURE, SECRET));
    }

    @Test
    @DisplayName("Sin secreto configurado no se valida la firma (modo desarrollo)")
    void sinSecretoNoValidaLaFirma() {
        assertTrue(MercadoPagoSignature.isValid(DATA_ID, REQUEST_ID, null, ""));
        assertTrue(MercadoPagoSignature.isValid(DATA_ID, REQUEST_ID, null, null));
    }
}
