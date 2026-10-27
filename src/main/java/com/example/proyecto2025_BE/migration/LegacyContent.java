package com.example.proyecto2025_BE.migration;

import java.util.List;

public record LegacyContent(
        List<LegacyQuestion> questions,
        List<LegacyFeedback> feedback,
        List<LegacyGenerationJob> jobs
) {
}
