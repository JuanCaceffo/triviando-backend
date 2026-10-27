package com.example.proyecto2025_BE.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trabajo asíncrono de generación de preguntas a partir de un archivo.
 * Guarda el texto extraído mientras se procesa y lo limpia al terminar.
 * La limpieza de jobs viejos queda a cargo de un proceso programado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "generation_jobs")
public class GenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Account role;

    @Column(length = 120)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private int progress;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<GeneratedTopic> result;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    public boolean isFinished() {
        return JobStatus.COMPLETED.equals(status) || JobStatus.FAILED.equals(status);
    }
}
