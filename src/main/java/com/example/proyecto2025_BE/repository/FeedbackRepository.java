package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}
