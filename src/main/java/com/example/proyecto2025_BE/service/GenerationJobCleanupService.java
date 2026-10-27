package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Reemplaza el TTL de 24 h que tenía la colección Mongo de jobs.
 * Se ejecuta cada hora y borra los jobs cuya creación supera la retención.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationJobCleanupService {

    static final int RETENTION_HOURS = 24;

    private final GenerationJobRepository repository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(RETENTION_HOURS);
        int deleted = repository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Se eliminaron {} jobs de generación anteriores a {}", deleted, cutoff);
        }
    }
}
