package com.example.proyecto2025_BE.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class PaymentGatewayException extends RuntimeException {

	private static final long serialVersionUID = 729145830412987654L;

	public PaymentGatewayException(String message) {
		super(message);
	}

	public PaymentGatewayException(String message, Throwable cause) {
		super(message, cause);
	}

	public static PaymentGatewayException build(String message) {
		return new PaymentGatewayException(message);
	}
}
