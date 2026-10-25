package com.example.proyecto2025_BE.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Limites y presupuestos para la generación de preguntas a partir de archivos.
 */
@Configuration
@ConfigurationProperties(prefix = "api.documents")
@Data
public class DocumentProperties {

    /**
     * Tamaño máximo del archivo subido (10 MB por defecto).
     */
    private long maxFileSizeBytes = 10 * 1024 * 1024L;

    /**
     * Máximo de caracteres que se retienen del texto extraído.
     */
    private int maxExtractedChars = 400_000;

    /**
     * Máximo de caracteres de contexto que se envían al LLM (~8k tokens).
     */
    private int promptBudgetChars = 30_000;

    /**
     * Tamaño de cada bloque que se resume en la fase map.
     */
    private int chunkSizeChars = 8_000;

    /**
     * Cantidad máxima de bloques a resumir. Acota costo y latencia.
     */
    private int maxChunks = 10;

    /**
     * Cantidad máxima de páginas de un PDF que se procesan.
     */
    private int maxPdfPages = 200;

    /**
     * Minutos sin avance tras los cuales un job se considera expirado.
     */
    private int staleJobMinutes = 15;

    /**
     * Si es true, un usuario no puede tener más de un job activo a la vez.
     */
    private boolean oneActiveJobPerUser = true;
}
