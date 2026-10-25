package com.example.proyecto2025_BE.exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.LangChain4jException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DocumentProperties documentProperties;

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflictException(ConflictException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = responseStatus != null ? responseStatus.value() : HttpStatus.CONFLICT;
        return new ResponseEntity<>(errors, status);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Object> handleGeneralExceptions(Exception ex) {
        log.error("Ocurrió una excepción no controlada: ", ex);
        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage() == null ? "Ocurrio un error inesperado." : ex.getMessage());
        ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        return new ResponseEntity<>(errors, responseStatus != null ? responseStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFoundtException(NotFoundException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = responseStatus != null ? responseStatus.value() : HttpStatus.NOT_FOUND;
        return new ResponseEntity<>(errors, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessages = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", errorMessages);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidationException(ValidationException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        log.warn("Validation error: {}", ex.getMessage());
        ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = responseStatus != null ? responseStatus.value() : HttpStatus.BAD_REQUEST;
        return new ResponseEntity<>(errors, status);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(LangChain4jException.class)
    public ResponseEntity<?> handleLangChain4jException(LangChain4jException ex) {
        try {
            Map<?, ?> errorDetails = objectMapper.readValue(ex.getMessage(), Map.class);
            log.error("LangChain4j error: {}", ex.getMessage());
            return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
        } catch (JsonProcessingException e) {
            Map<String, Object> errors = new HashMap<>();
            errors.put("error", ex.getMessage());
            return new ResponseEntity<>(errors, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Archivo subido supera el tamaño máximo permitido: {}", ex.getMessage());
        long maxMegabytes = Math.max(1, documentProperties.getMaxFileSizeBytes() / (1024 * 1024));
        Map<String, String> errors = new HashMap<>();
        errors.put("error", String.format(Exceptions.FILE_TOO_LARGE, maxMegabytes));
        return new ResponseEntity<>(errors, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Object> handleMultipartException(Exception ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage() == null ? Exceptions.INVALID_FILE : ex.getMessage());
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
}
