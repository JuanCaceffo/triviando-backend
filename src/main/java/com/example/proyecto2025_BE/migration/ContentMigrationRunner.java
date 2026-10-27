package com.example.proyecto2025_BE.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada CLI del migrador. Se activa solo con el perfil
 * {@code migrate} y, por defecto, cierra la aplicación al terminar.
 */
@Slf4j
@Component
@Profile("migrate")
@RequiredArgsConstructor
public class ContentMigrationRunner implements ApplicationRunner {

    private final ContentMigrationService service;
    private final MigrationProperties properties;
    private final ApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Iniciando migración Mongo -> MySQL (dryRun={}, batchSize={})",
                properties.isDryRun(), properties.getBatchSize());
        try {
            MigrationReport report = service.run();
            log.info("Resultado de la migración:\n{}", report.summary());
            finish(0);
        } catch (Exception e) {
            log.error("Migración abortada: {}", e.getMessage(), e);
            finish(1);
            throw e;
        }
    }

    private void finish(int exitCode) {
        if (properties.isExitAfterRun()) {
            System.exit(SpringApplication.exit(context, () -> exitCode));
        }
    }
}
