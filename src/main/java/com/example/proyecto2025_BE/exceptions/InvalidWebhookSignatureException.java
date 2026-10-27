package com.example.proyecto2025_BE.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidWebhookSignatureException extends RuntimeException {

	private static final long serialVersionUID = 314598736452109876L;

	public InvalidWebhookSignatureException(String message) {
		super(message);
	}

	public static InvalidWebhookSignatureException build(String message) {
		return new InvalidWebhookSignatureException(message);
	}
}
