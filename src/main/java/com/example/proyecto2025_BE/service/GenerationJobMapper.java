package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.model.GeneratedTopic;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.views.documents.GenerationJobCreatedResponse;
import com.example.proyecto2025_BE.views.documents.GenerationJobResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduce entre el job persistido y los DTOs de la API / resultado embebido.
 */
@Component
public class GenerationJobMapper {

    public GenerationJobCreatedResponse toCreatedResponse(GenerationJob job) {
        return GenerationJobCreatedResponse.builder()
                .jobId(job.getId())
                .topic(job.getTopic())
                .status(job.getStatus())
                .progress(job.getProgress())
                .build();
    }

    public GenerationJobResponse toResponse(GenerationJob job) {
        List<Topics> result = job.getResult() == null ? null : job.getResult().stream()
                .map(this::toTopics)
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

    public List<GeneratedTopic> toGeneratedTopics(List<Topics> topics) {
        if (topics == null) {
            return List.of();
        }
        return topics.stream().map(this::toGeneratedTopic).toList();
    }

    private Topics toTopics(GeneratedTopic generated) {
        return Topics.builder()
                .topic(generated.getTopic())
                .size(generated.getSize())
                .emoji(generated.getEmoji())
                .build();
    }

    private GeneratedTopic toGeneratedTopic(Topics topic) {
        return GeneratedTopic.builder()
                .topic(topic.topic())
                .size(topic.size().intValue())
                .emoji(topic.emoji())
                .build();
    }
}
