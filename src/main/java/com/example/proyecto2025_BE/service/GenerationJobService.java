package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.GenerationProgress;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Ciclo de vida del job: creación, búsqueda con ownership y expiración
 * de jobs que quedaron sin avance.
 */
@Service
@RequiredArgsConstructor
public class GenerationJobService {

    private final GenerationJobRepository repository;
    private final DocumentProperties properties;

    public GenerationJob create(Long userId, Account role, String topic, String extractedText) {
        LocalDateTime now = LocalDateTime.now();
        return repository.save(GenerationJob.builder()
                .userId(userId)
                .role(role)
                .topic(topic)
                .status(JobStatus.PENDING)
                .progress(GenerationProgress.QUEUED)
                .extractedText(extractedText)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    public GenerationJob getOwned(Long jobId, Long userId) {
        GenerationJob job = repository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> NotFoundException.build(Exceptions.GENERATION_JOB_NOT_FOUND));
        expireIfStale(job);
        return job;
    }

    private void expireIfStale(GenerationJob job) {
        if (job.isFinished() || job.getUpdatedAt() == null) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(properties.getStaleJobMinutes());
        if (job.getUpdatedAt().isBefore(threshold)) {
            job.setStatus(JobStatus.FAILED);
            job.setError(Exceptions.GENERATION_JOB_EXPIRED);
            job.setExtractedText(null);
            job.setUpdatedAt(LocalDateTime.now());
            repository.save(job);
        }
    }
}
