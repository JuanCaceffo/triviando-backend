package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.model.prompter.FilePrompter;
import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import com.example.proyecto2025_BE.views.documents.GenerationJobCreatedResponse;
import com.example.proyecto2025_BE.views.documents.GenerationJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentGenerationService implements IDocumentGeneration {

    private static final List<JobStatus> ACTIVE_STATUSES = List.of(JobStatus.PENDING, JobStatus.PROCESSING);

    private final GenerationJobRepository repository;
    private final DocumentTextExtractor textExtractor;
    private final DocumentGenerationWorker worker;
    private final DocumentProperties properties;
    private final IQuestion questionService;

    @Override
    public GenerationJobCreatedResponse enqueue(String topic, MultipartFile file, Account role, Long userId) {
        String cleanTopic = validateTopic(topic);
        validateBusinessRules(cleanTopic, role, userId);
        String contextText = textExtractor.extract(file);

        LocalDateTime now = LocalDateTime.now();
        GenerationJob job = repository.save(GenerationJob.builder()
                .userId(userId)
                .role(role)
                .topic(cleanTopic)
                .status(JobStatus.PENDING)
                .progress(0)
                .contextText(contextText)
                .createdAt(now)
                .updatedAt(now)
                .build());

        worker.process(job.getId());

        return GenerationJobCreatedResponse.builder()
                .jobId(job.getId())
                .topic(job.getTopic())
                .status(job.getStatus())
                .progress(job.getProgress())
                .build();
    }

    @Override
    public GenerationJobResponse getJob(String jobId, Long userId) {
        GenerationJob job = repository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> NotFoundException.build(Exceptions.GENERATION_JOB_NOT_FOUND));

        expireIfStale(job);

        return toResponse(job);
    }

    private String validateTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw ValidationException.build(Exceptions.GENERATION_TOPIC_REQUIRED);
        }
        return topic.trim();
    }

    /**
     * Reutiliza las reglas de {@link com.example.proyecto2025_BE.model.prompter.TopicPrompter}:
     * no crear tópicos duplicados y respetar el límite diario del plan FREE.
     * Se valida antes de leer el archivo para fallar rápido.
     */
    private void validateBusinessRules(String topic, Account role, Long userId) {
        if (properties.isOneActiveJobPerUser()
                && repository.findFirstByUserIdAndStatusIn(userId, ACTIVE_STATUSES).isPresent()) {
            throw ConflictException.build(Exceptions.GENERATION_JOB_IN_PROGRESS);
        }

        FilePrompter prompter = FilePrompter.builder()
                .topic(topic)
                .userId(userId)
                .build();
        prompter.withService(questionService);
        prompter.validatePrompt();

        if (Account.FREE.equals(role)) {
            prompter.validateGeneration(userId);
        }
    }

    private void expireIfStale(GenerationJob job) {
        if (job.isFinished() || job.getUpdatedAt() == null) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(properties.getStaleJobMinutes());
        if (job.getUpdatedAt().isBefore(threshold)) {
            job.setStatus(JobStatus.FAILED);
            job.setError(Exceptions.GENERATION_JOB_EXPIRED);
            job.setContextText(null);
            job.setUpdatedAt(LocalDateTime.now());
            repository.save(job);
        }
    }

    private GenerationJobResponse toResponse(GenerationJob job) {
        List<Topics> result = job.getResult() == null ? null : job.getResult().stream()
                .map(generated -> Topics.builder()
                        .topic(generated.getTopic())
                        .size(generated.getSize())
                        .emoji(generated.getEmoji())
                        .build())
                .toList();

        return GenerationJobResponse.builder()
                .jobId(job.getId())
                .topic(job.getTopic())
                .status(job.getStatus())
                .progress(job.getProgress())
                .result(result)
                .error(job.getError())
                .build();
    }
}
