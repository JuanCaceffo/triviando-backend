package com.example.proyecto2025_BE.views.documents;

import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.dto.Topics;
import lombok.Builder;

import java.util.List;

@Builder
public record GenerationJobResponse(
        Long jobId,
        String topic,
        JobStatus status,
        int progress,
        List<Topics> result,
        String error
) {
}
