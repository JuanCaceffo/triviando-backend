package com.example.proyecto2025_BE.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Permite el envío de emails en un hilo aparte (@Async) para no bloquear la respuesta.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
