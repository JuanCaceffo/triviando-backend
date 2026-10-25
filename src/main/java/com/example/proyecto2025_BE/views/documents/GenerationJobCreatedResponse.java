package com.example.proyecto2025_BE.views.documents;

import com.example.proyecto2025_BE.model.JobStatus;
import lombok.Builder;

@Builder
public record GenerationJobCreatedResponse(
        String jobId,
        String topic,
        JobStatus status,
        int progress
) {
}
