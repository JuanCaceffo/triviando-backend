package com.example.proyecto2025_BE.migration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del proceso de migración de contenido desde MongoDB a MySQL.
 *
 * El proceso se ejecuta con los perfiles {@code prod,migrate} (o
 * {@code dev,migrate}) y por defecto no escribe nada: hay que pasar
 * {@code --app.migration.dry-run=false} de forma explícita.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.migration")
public class MigrationProperties {

    /** Si es true, solo analiza y reporta; no escribe en MySQL. */
    private boolean dryRun = true;

    /** Si es true, cierra la aplicación al terminar (modo CLI). */
    private boolean exitAfterRun = true;

    /** Cantidad de preguntas por transacción. */
    private int batchSize = 100;

    /** Ventana de retención para migrar jobs efímeros. */
    private int jobsWindowHours = 24;

    /** Si es true, aborta ante usuarios/huérfanos o preguntas inválidas. */
    private boolean failOnOrphans = true;

    private String questionsCollection = "preguntas";
    private String feedbackCollection = "feedback";
    private String jobsCollection = "generation_jobs";
}
