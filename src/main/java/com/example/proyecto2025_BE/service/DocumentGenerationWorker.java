package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.GenerationProgress;
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

    private final GenerationJobRepository repository;
    private final DocumentContextBuilder contextBuilder;
    private final LLMApiClientService llmApiClientService;
    private final GenerationJobMapper mapper;

    @Async("documentGenerationExecutor")
    public void process(String jobId) {
        GenerationJob job = repository.findById(jobId).orElse(null);
        if (job == null || !JobStatus.PENDING.equals(job.getStatus())) {
            return;
        }

        String extractedText = job.getExtractedText();
        // Se limpia el texto crudo para no reescribirlo en cada actualización de progreso
        job.setExtractedText(null);
        job.setStatus(JobStatus.PROCESSING);
        updateProgress(job, GenerationProgress.STARTED);

        try {
            String summarizedContext = contextBuilder.buildContext(extractedText,
                    percentage -> updateProgress(job, GenerationProgress.fromSummaryPercentage(percentage)));
            updateProgress(job, GenerationProgress.CONTEXT_READY);

            List<Topics> topics = llmApiClientService.generate(
                    buildPrompter(job, summarizedContext), job.getRole(), job.getUserId());

            job.setResult(mapper.toGeneratedTopics(topics));
            job.setStatus(JobStatus.COMPLETED);
            job.setError(null);
            updateProgress(job, GenerationProgress.DONE);
        } catch (Exception e) {
            log.error("Falló la generación desde archivo para el job {}", job.getId(), e);
            job.setStatus(JobStatus.FAILED);
            job.setError(userFacingError(e));
            job.setExtractedText(null);
            job.setUpdatedAt(LocalDateTime.now());
            repository.save(job);
        }
    }

    private FilePrompter buildPrompter(GenerationJob job, String summarizedContext) {
        return FilePrompter.forDocument(job.getTopic(), job.getUserId(), summarizedContext);
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
        return Exceptions.GENERATION_FAILED;
    }
}
