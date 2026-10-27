package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.service.document.DocumentTextExtractionService;
import com.example.proyecto2025_BE.views.documents.GenerationJobCreatedResponse;
import com.example.proyecto2025_BE.views.documents.GenerationJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orquesta una generación desde archivo: valida la solicitud, extrae el texto,
 * crea el job y lo encola. El trabajo pesado queda en {@link DocumentGenerationWorker}.
 */
@Service
@RequiredArgsConstructor
public class DocumentGenerationService implements IDocumentGeneration {

    private final GenerationRequestPolicy policy;
    private final DocumentTextExtractionService textExtractionService;
    private final GenerationJobService jobService;
    private final GenerationJobMapper mapper;
    private final DocumentGenerationWorker worker;

    @Override
    public GenerationJobCreatedResponse enqueue(String topic, MultipartFile file, Account role, Long userId) {
        String cleanTopic = policy.validateRequest(topic, role, userId);
        String extractedText = textExtractionService.extract(file);

        GenerationJob job = jobService.create(userId, role, cleanTopic, extractedText);
        worker.process(job.getId());

        return mapper.toCreatedResponse(job);
    }

    @Override
    public GenerationJobResponse getJob(Long jobId, Long userId) {
        return mapper.toResponse(jobService.getOwned(jobId, userId));
    }
}
