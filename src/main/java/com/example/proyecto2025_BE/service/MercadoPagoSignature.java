package com.example.proyecto2025_BE.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.util.StringUtils;

/**
 * Valida el header {@code x-signature} que envía Mercado Pago en cada webhook.
 *
 * <p>El manifiesto firmado es {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}
 * y se calcula un HMAC-SHA256 con la clave secreta del webhook configurada en el panel.</p>
 */
public final class MercadoPagoSignature {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private MercadoPagoSignature() {
	}

	public static boolean isValid(String dataId, String requestId, String xSignature, String secret) {
		// Sin secreto configurado (entornos de desarrollo) no se exige firma.
		if (!StringUtils.hasText(secret)) {
			return true;
		}
		if (!StringUtils.hasText(dataId) || !StringUtils.hasText(requestId) || !StringUtils.hasText(xSignature)) {
			return false;
		}

		Map<String, String> parts = parseSignatureHeader(xSignature);
		String timestamp = parts.get("ts");
		String receivedHash = parts.get("v1");
		if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(receivedHash)) {
			return false;
		}

		String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
		String expectedHash = hmacSha256Hex(manifest, secret);
		if (expectedHash == null) {
			return false;
		}

		return MessageDigest.isEqual(
				expectedHash.getBytes(StandardCharsets.UTF_8),
				receivedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
	}

	private static Map<String, String> parseSignatureHeader(String xSignature) {
		Map<String, String> parts = new HashMap<>();
		for (String part : xSignature.split(",")) {
			int separator = part.indexOf('=');
			if (separator > 0) {
				parts.put(part.substring(0, separator).trim(), part.substring(separator + 1).trim());
			}
		}
		return parts;
	}

	private static String hmacSha256Hex(String value, String secret) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			return null;
		}
	}
}
