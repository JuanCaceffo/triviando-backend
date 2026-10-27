package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findByUserIdAndNameNormalized(Long userId, String nameNormalized);

    boolean existsByUserIdAndNameNormalized(Long userId, String nameNormalized);
}
