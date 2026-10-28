package com.example.proyecto2025_BE.model;

import com.example.proyecto2025_BE.utils.ContentNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tópico de un usuario. El nombre y el emoji tienen una única fila;
 * antes estaban repetidos en cada pregunta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "topics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_topics_user_name",
                columnNames = {"user_id", "name_normalized"}))
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = ContentNormalizer.MAX_TOPIC_LENGTH)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = ContentNormalizer.MAX_TOPIC_LENGTH)
    private String nameNormalized;

    @Column(length = 16)
    private String emoji;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (nameNormalized == null) {
            nameNormalized = ContentNormalizer.topicKey(name);
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
