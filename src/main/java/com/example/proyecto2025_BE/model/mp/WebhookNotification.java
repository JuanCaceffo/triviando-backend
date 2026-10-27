package com.example.proyecto2025_BE.model.mp;

/**
 * Notificación de Mercado Pago tal como llega al webhook: puede venir con el
 * detalle en el body (JSON) o solo con query params, más los headers de firma.
 */
public record WebhookNotification(
        String payload,
        String dataId,
        String type,
        String signature,
        String requestId
) {
}
