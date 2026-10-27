package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.views.documents.GenerationJobCreatedResponse;
import com.example.proyecto2025_BE.views.documents.GenerationJobResponse;
import org.springframework.web.multipart.MultipartFile;

public interface IDocumentGeneration {

    GenerationJobCreatedResponse enqueue(String topic, MultipartFile file, Account role, Long userId);

    GenerationJobResponse getJob(Long jobId, Long userId);
}
