package com.example.proyecto2025_BE.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

	private static final long serialVersionUID = 482913775642198765L;

	private ForbiddenException(String message) {
		super(message);
	}

	public static ForbiddenException build(String message) {
		return new ForbiddenException(message);
	}
}
