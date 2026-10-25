package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.GeneratedTopic;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.model.prompter.FilePrompter;
import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ejecuta el trabajo pesado en un hilo aparte: resumen del documento,
 * generación con el LLM y persistencia de las preguntas. Los errores se
 * reflejan en el estado del job para que el frontend los muestre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentGenerationWorker {

    private static final int PROGRESS_START = 5;
    private static final int PROGRESS_CONTEXT_READY = 65;
    private static final int PROGRESS_DONE = 100;
    private static final int MAX_ERROR_LENGTH = 300;

    private final GenerationJobRepository repository;
    private final DocumentContextBuilder contextBuilder;
    private final LLMApiClientService llmApiClientService;

    @Async("documentGenerationExecutor")
    public void process(String jobId) {
        GenerationJob job = repository.findById(jobId).orElse(null);
        if (job == null || !JobStatus.PENDING.equals(job.getStatus())) {
            return;
        }

        String rawText = job.getContextText();
        // Se limpia el texto crudo para no reescribirlo en cada actualización de progreso
        job.setContextText(null);
        job.setStatus(JobStatus.PROCESSING);
        updateProgress(job, PROGRESS_START);

        try {
            String context = contextBuilder.buildContext(rawText,
                    percentage -> updateProgress(job, mapChunkProgress(percentage)));
            updateProgress(job, PROGRESS_CONTEXT_READY);

            List<Topics> topics = llmApiClientService.generate(buildPrompter(job, context), job.getRole(), job.getUserId());

            job.setResult(topics.stream()
                    .map(topic -> GeneratedTopic.builder()
                            .topic(topic.topic())
                            .size(topic.size().intValue())
                            .emoji(topic.emoji())
                            .build())
                    .toList());
            job.setStatus(JobStatus.COMPLETED);
            job.setError(null);
            updateProgress(job, PROGRESS_DONE);
        } catch (Exception e) {
            log.error("Falló la generación desde archivo para el job {}", job.getId(), e);
            job.setStatus(JobStatus.FAILED);
            job.setError(userFacingError(e));
            job.setContextText(null);
            job.setUpdatedAt(LocalDateTime.now());
            repository.save(job);
        }
    }

    private FilePrompter buildPrompter(GenerationJob job, String context) {
        return FilePrompter.builder()
                .topic(job.getTopic())
                .userId(job.getUserId())
                .context(context)
                .build();
    }

    private int mapChunkProgress(int percentage) {
        int bounded = Math.max(0, Math.min(100, percentage));
        return 10 + (bounded * 50 / 100);
    }

    /**
     * Serializa las actualizaciones para que los resúmenes en paralelo no
     * escriban progreso desordenado en Mongo.
     */
    private synchronized void updateProgress(GenerationJob job, int progress) {
        if (progress <= job.getProgress()) {
            return;
        }
        job.setProgress(progress);
        job.setUpdatedAt(LocalDateTime.now());
        repository.save(job);
    }

    private String userFacingError(Exception exception) {
        if (exception instanceof ValidationException) {
            return exception.getMessage();
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return Exceptions.GENERATION_FAILED;
        }
        String sanitized = message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH)
                : message;
        return Exceptions.GENERATION_FAILED + ": " + sanitized;
    }
}
