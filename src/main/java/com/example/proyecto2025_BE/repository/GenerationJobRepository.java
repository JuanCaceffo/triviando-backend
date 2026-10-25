package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.JobStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.Optional;

public interface GenerationJobRepository extends MongoRepository<GenerationJob, String> {

    Optional<GenerationJob> findByIdAndUserId(String id, Long userId);

    Optional<GenerationJob> findFirstByUserIdAndStatusIn(Long userId, Collection<JobStatus> statuses);
}
