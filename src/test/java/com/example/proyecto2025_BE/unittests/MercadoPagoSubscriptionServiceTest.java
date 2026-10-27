package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.MPConfig;
import com.example.proyecto2025_BE.exceptions.InvalidWebhookSignatureException;
import com.example.proyecto2025_BE.exceptions.PaymentGatewayException;
import com.example.proyecto2025_BE.model.mp.WebhookNotification;
import com.example.proyecto2025_BE.service.MercadoPagoSubscriptionService;
import com.example.proyecto2025_BE.service.UserService;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.net.MPResponse;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Mercado Pago Subscription Service Tests")
class MercadoPagoSubscriptionServiceTest {

    private static final String NOTIFICATION_URL = "https://tunel.ngrok-free.app/api/v1/suscriptions/payment";
    private static final String WEBHOOK_SECRET = "clave-secreta-webhook";
    private static final BigDecimal PLAN_PRICE = new BigDecimal("10000");

    @Mock
    private UserService userService;
    @Mock
    private PreferenceClient preferenceClient;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private PreferenceItemRequest preferenceItemRequest;
    @Mock
    private PreferenceBackUrlsRequest preferenceBackUrlsRequest;

    private MPConfig mpConfig;
    private MercadoPagoSubscriptionService service;

    @BeforeEach
    void setUp() {
        mpConfig = new MPConfig();
        mpConfig.setFeDomain("https://triviando-phi.vercel.app/");
        mpConfig.setAccessToken("TEST-token");
        mpConfig.setNotificationUrl(NOTIFICATION_URL);
        mpConfig.setWebhookSecret(null);
        mpConfig.setPlanPrice(PLAN_PRICE);

        service = new MercadoPagoSubscriptionService(
                userService,
                preferenceClient,
                paymentClient,
                preferenceItemRequest,
                preferenceBackUrlsRequest,
                mpConfig);
    }

    @Test
    @DisplayName("La preferencia informa la URL de notificación y el email del comprador")
    void laPreferenciaInformaLaUrlDeNotificacion() throws Exception {
        Preference preference = mock(Preference.class);
        when(preference.getInitPoint()).thenReturn("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=123");
        when(preferenceClient.create(any(PreferenceRequest.class))).thenReturn(preference);

        String initPoint = service.createSubscriptionPreference("alumno@unsam.edu.ar");

        ArgumentCaptor<PreferenceRequest> captor = ArgumentCaptor.forClass(PreferenceRequest.class);
        verify(preferenceClient).create(captor.capture());
        assertEquals(NOTIFICATION_URL, captor.getValue().getNotificationUrl());
        assertEquals("alumno@unsam.edu.ar", captor.getValue().getExternalReference());
        assertEquals("approved", captor.getValue().getAutoReturn());
        assertEquals("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=123", initPoint);
    }

    @Test
    @DisplayName("Sin dominio HTTPS no se envía auto_return")
    void sinDominioHttpsNoSeEnviaAutoReturn() throws Exception {
        mpConfig.setFeDomain("http://localhost:3000/");
        Preference preference = mock(Preference.class);
        when(preference.getInitPoint()).thenReturn("https://mp/init");
        when(preferenceClient.create(any(PreferenceRequest.class))).thenReturn(preference);

        service.createSubscriptionPreference("alumno@unsam.edu.ar");

        ArgumentCaptor<PreferenceRequest> captor = ArgumentCaptor.forClass(PreferenceRequest.class);
        verify(preferenceClient).create(captor.capture());
        assertNull(captor.getValue().getAutoReturn());
    }

    @Test
    @DisplayName("Un error de Mercado Pago se propaga como PaymentGatewayException")
    void unErrorDeMercadoPagoSePropagaComoPaymentGatewayException() throws Exception {
        MPApiException apiException = new MPApiException(
                "auto_return invalid",
                new MPResponse(400, Map.of(), "auto_return invalid"));
        when(preferenceClient.create(any(PreferenceRequest.class))).thenThrow(apiException);

        assertThrows(PaymentGatewayException.class,
                () -> service.createSubscriptionPreference("alumno@unsam.edu.ar"));
    }

    @Test
    @DisplayName("Un pago aprobado marca al usuario como premium")
    void unPagoAprobadoMarcaAlUsuarioComoPremium() throws Exception {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn("approved");
        when(payment.getTransactionAmount()).thenReturn(PLAN_PRICE);
        when(payment.getCurrencyId()).thenReturn("ARS");
        when(payment.getExternalReference()).thenReturn("alumno@unsam.edu.ar");
        when(paymentClient.get(123L)).thenReturn(payment);

        service.notifyPayment(new WebhookNotification(
                null, "123", "payment", null, null));

        verify(userService).markUserAsSubscribed("alumno@unsam.edu.ar");
    }

    @Test
    @DisplayName("Un pago pendiente no marca al usuario como premium")
    void unPagoPendienteNoMarcaAlUsuarioComoPremium() throws Exception {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn("pending");
        when(paymentClient.get(123L)).thenReturn(payment);

        service.notifyPayment(new WebhookNotification(
                null, "123", "payment", null, null));

        verify(userService, never()).markUserAsSubscribed(any());
    }

    @Test
    @DisplayName("Un pago con monto distinto no marca al usuario como premium")
    void unPagoConMontoDistintoNoMarcaAlUsuarioComoPremium() throws Exception {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn("approved");
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal("1"));
        when(paymentClient.get(123L)).thenReturn(payment);

        service.notifyPayment(new WebhookNotification(
                null, "123", "payment", null, null));

        verify(userService, never()).markUserAsSubscribed(any());
    }

    @Test
    @DisplayName("Un pago en otra moneda no marca al usuario como premium")
    void unPagoEnOtraMonedaNoMarcaAlUsuarioComoPremium() throws Exception {
        when(preferenceItemRequest.getCurrencyId()).thenReturn("ARS");
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn("approved");
        when(payment.getTransactionAmount()).thenReturn(PLAN_PRICE);
        when(payment.getCurrencyId()).thenReturn("USD");
        when(paymentClient.get(123L)).thenReturn(payment);

        service.notifyPayment(new WebhookNotification(
                null, "123", "payment", null, null));

        verify(userService, never()).markUserAsSubscribed(any());
    }

    @Test
    @DisplayName("Toma el id del pago desde el body JSON de la notificación")
    void tomaElIdDelPagoDesdeElBodyJson() throws Exception {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn("approved");
        when(payment.getTransactionAmount()).thenReturn(PLAN_PRICE);
        when(payment.getCurrencyId()).thenReturn("ARS");
        when(payment.getExternalReference()).thenReturn("alumno@unsam.edu.ar");
        when(paymentClient.get(123L)).thenReturn(payment);

        String body = """
                {
                  "action": "payment.created",
                  "api_version": "v1",
                  "data": { "id": "123" },
                  "date_created": "2025-07-15T00:16:02Z",
                  "type": "payment"
                }
                """;

        service.notifyPayment(new WebhookNotification(body, null, null, null, null));

        verify(userService).markUserAsSubscribed("alumno@unsam.edu.ar");
    }

    @Test
    @DisplayName("Ignora notificaciones que no son del tópico payment")
    void ignoraNotificacionesQueNoSonDePago() {
        service.notifyPayment(new WebhookNotification(
                null, "123", "merchant_order", null, null));

        verify(userService, never()).markUserAsSubscribed(any());
    }

    @Test
    @DisplayName("Ignora otros tópicos sin exigir firma válida")
    void ignoraOtrosTopicosSinValidarFirma() {
        mpConfig.setWebhookSecret(WEBHOOK_SECRET);

        service.notifyPayment(new WebhookNotification(
                null, "45590070969", "merchant_order",
                "ts=1704908010,v1=deadbeef", "e47c9b7d-5c3a-4b7f-9a1e-2d3c4b5a6f70"));

        verify(userService, never()).markUserAsSubscribed(any());
        verifyNoInteractions(paymentClient);
    }

    @Test
    @DisplayName("Una firma inválida rechaza la notificación sin consultar el pago")
    void firmaInvalidaRechazaLaNotificacion() {
        mpConfig.setWebhookSecret(WEBHOOK_SECRET);

        assertThrows(InvalidWebhookSignatureException.class, () -> service.notifyPayment(
                new WebhookNotification(null, "123", "payment",
                        "ts=1704908010,v1=0000000000000000000000000000000000000000000000000000000000000000",
                        "e47c9b7d-5c3a-4b7f-9a1e-2d3c4b5a6f70")));

        verify(userService, never()).markUserAsSubscribed(any());
    }
}
