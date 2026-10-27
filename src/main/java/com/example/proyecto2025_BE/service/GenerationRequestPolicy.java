package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.ForbiddenException;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reglas de negocio que deben cumplirse antes de encolar una generación
 * desde archivo: plan Premium, tópico válido y nuevo y sin otro job activo.
 */
@Component
@RequiredArgsConstructor
public class GenerationRequestPolicy {

    private static final List<JobStatus> ACTIVE_STATUSES =
            List.of(JobStatus.PENDING, JobStatus.PROCESSING);

    private final GenerationJobRepository repository;
    private final DocumentProperties properties;
    private final IQuestion questionService;

    /**
     * @return el nombre del tópico normalizado
     */
    public String validateRequest(String topic, Account role, Long userId) {
        String cleanTopic = normalizeTopic(topic);
        ensurePremium(role);
        ensureNoActiveJob(userId);
        ensureTopicIsNew(cleanTopic, userId);
        return cleanTopic;
    }

    private String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw ValidationException.build(Exceptions.GENERATION_TOPIC_REQUIRED);
        }
        return topic.trim();
    }

    private void ensureNoActiveJob(Long userId) {
        if (properties.isOneActiveJobPerUser()
                && repository.findFirstByUserIdAndStatusIn(userId, ACTIVE_STATUSES).isPresent()) {
            throw ConflictException.build(Exceptions.GENERATION_JOB_IN_PROGRESS);
        }
    }

    private void ensureTopicIsNew(String topic, Long userId) {
        if (questionService.existsByTopicAndUser(topic, userId)) {
            throw ConflictException.build(Exceptions.TOPIC_ALREADY_EXISTS);
        }
    }

    private void ensurePremium(Account role) {
        if (!Account.PREMIUM.equals(role)) {
            throw ForbiddenException.build(Exceptions.PREMIUM_ONLY_DOCUMENT_GENERATION);
        }
    }
}
