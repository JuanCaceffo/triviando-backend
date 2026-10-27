package com.example.proyecto2025_BE.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.proyecto2025_BE.configuration.MPConfig;
import com.example.proyecto2025_BE.configuration.MPSerializer;
import com.example.proyecto2025_BE.exceptions.InvalidWebhookSignatureException;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.exceptions.PaymentGatewayException;
import com.example.proyecto2025_BE.model.mp.PaymentNotification;
import com.example.proyecto2025_BE.model.mp.WebhookNotification;
import com.example.proyecto2025_BE.views.subscriptions.SubscriptionPlanResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoPagoSubscriptionService {

	private static final String PLAN_BILLING_TYPE = "ONE_TIME";
	private static final String PAYMENT_TOPIC = "payment";
	private static final String APPROVED_STATUS = "approved";

	private final UserService userService;
    private final PreferenceClient preferenceClient;
    private final PaymentClient paymentClient;
    private final PreferenceItemRequest preferenceItemRequest;
    private final PreferenceBackUrlsRequest preferenceBackUrlsRequest;
    private final MPConfig mpConfig;

    public SubscriptionPlanResponse getPlan() {
        return new SubscriptionPlanResponse(
                preferenceItemRequest.getUnitPrice(),
                preferenceItemRequest.getCurrencyId(),
                PLAN_BILLING_TYPE);
    }

    public String createSubscriptionPreference(String email) {
    	PreferenceRequest.PreferenceRequestBuilder requestBuilder = PreferenceRequest.builder()
                .items(Collections.singletonList(preferenceItemRequest))
                .backUrls(preferenceBackUrlsRequest)
                .externalReference(email);

    	if (mpConfig.hasSecureBackUrls()) {
    		requestBuilder.autoReturn(APPROVED_STATUS);
    	}
    	if (StringUtils.hasText(mpConfig.getNotificationUrl())) {
    		requestBuilder.notificationUrl(mpConfig.getNotificationUrl());
    	}

    	Preference preference;
		try {
			preference = preferenceClient.create(requestBuilder.build());
		} catch (MPApiException e) {
			String detail = e.getApiResponse() == null ? e.getMessage() : e.getApiResponse().getContent();
			log.error("Mercado Pago rechazó la creación de la preferencia: {}", detail);
			throw new PaymentGatewayException(
					"No se pudo crear el checkout de Mercado Pago. Intentá nuevamente en unos minutos.", e);
		} catch (MPException e) {
			log.error("Error de comunicación con Mercado Pago al crear la preferencia", e);
			throw new PaymentGatewayException(
					"No se pudo comunicar con Mercado Pago. Intentá nuevamente en unos minutos.", e);
		}

		return Optional.ofNullable(preference)
				.map(Preference::getInitPoint)
				.filter(StringUtils::hasText)
				.orElseThrow(() -> new PaymentGatewayException(
						"Mercado Pago no devolvió un link de pago válido. Intentá nuevamente en unos minutos."));
    }

	public String notifyPayment(WebhookNotification notification) {
		PaymentNotification payload = parsePayload(notification.payload());

		String type = firstNonBlank(notification.type(), payload != null ? payload.getType() : null);
		String dataId = firstNonBlank(
				notification.dataId(),
				payload != null && payload.getData() != null ? payload.getData().getId() : null);

		if (StringUtils.hasText(type) && !PAYMENT_TOPIC.equalsIgnoreCase(type)) {
			log.info("Notificación de Mercado Pago ignorada por tipo: {}", type);
			return "Notificación ignorada";
		}

		if (!MercadoPagoSignature.isValid(dataId, notification.requestId(), notification.signature(),
				mpConfig.getWebhookSecret())) {
			log.warn("Notificación de Mercado Pago con firma inválida (type={}, data.id={})", type, dataId);
			throw InvalidWebhookSignatureException.build("La firma de la notificación de Mercado Pago es inválida");
		}

		Long paymentId = parsePaymentId(dataId);
		if (paymentId == null) {
			log.warn("Notificación de Mercado Pago sin un id de pago válido (data.id={})", dataId);
			return "Notificación ignorada";
		}

		Payment payment = findPayment(paymentId);

		if (!APPROVED_STATUS.equalsIgnoreCase(payment.getStatus())) {
			log.info("Pago {} con estado {}: no se otorga el plan premium", paymentId, payment.getStatus());
			return "Pago no aprobado";
		}

		String rejection = validateAmount(payment);
		if (rejection != null) {
			log.warn("Pago {} rechazado para el plan premium: {}", paymentId, rejection);
			return rejection;
		}

		String email = payment.getExternalReference();
		if (!StringUtils.hasText(email)) {
			log.warn("Pago {} aprobado sin external_reference: no se puede identificar al usuario", paymentId);
			return "Pago sin referencia de usuario";
		}

		try {
			userService.markUserAsSubscribed(email);
		} catch (NotFoundException e) {
			log.warn("Pago {} aprobado para un usuario inexistente: {}", paymentId, email);
			return "Usuario no encontrado";
		}

		log.info("Usuario {} marcado como premium por el pago {}", email, paymentId);
		return "Webhook procesado correctamente";
	}

	private Payment findPayment(Long paymentId) {
		try {
			return paymentClient.get(paymentId);
		} catch (MPException | MPApiException e) {
			log.error("Error consultando el pago {} en Mercado Pago", paymentId, e);
			throw new PaymentGatewayException("No se pudo verificar el pago con Mercado Pago.", e);
		}
	}

	private String validateAmount(Payment payment) {
		BigDecimal expectedAmount = mpConfig.getPlanPrice();
		if (expectedAmount == null || payment.getTransactionAmount() == null
				|| expectedAmount.compareTo(payment.getTransactionAmount()) != 0) {
			return "El monto del pago no coincide con el plan premium";
		}

		String expectedCurrency = preferenceItemRequest.getCurrencyId();
		if (StringUtils.hasText(payment.getCurrencyId()) && expectedCurrency != null
				&& !expectedCurrency.equalsIgnoreCase(payment.getCurrencyId())) {
			return "La moneda del pago no coincide con el plan premium";
		}

		return null;
	}

	private PaymentNotification parsePayload(String payload) {
		if (!StringUtils.hasText(payload)) {
			return null;
		}
		try {
			return MPSerializer.instance().readValue(payload, PaymentNotification.class);
		} catch (JsonProcessingException e) {
			log.warn("No se pudo interpretar el body de la notificación de Mercado Pago", e);
			return null;
		}
	}

	private Long parsePaymentId(String dataId) {
		if (!StringUtils.hasText(dataId)) {
			return null;
		}
		try {
			return Long.valueOf(dataId.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private String firstNonBlank(String first, String second) {
		return StringUtils.hasText(first) ? first : (StringUtils.hasText(second) ? second : null);
	}
}
