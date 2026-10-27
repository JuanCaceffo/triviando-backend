package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByIdAndUserId(Long id, Long userId);

    Optional<GenerationJob> findFirstByUserIdAndStatusIn(Long userId, Collection<JobStatus> statuses);

    @Modifying
    @Query("DELETE FROM GenerationJob j WHERE j.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
