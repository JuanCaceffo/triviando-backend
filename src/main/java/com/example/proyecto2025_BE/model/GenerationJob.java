package com.example.proyecto2025_BE.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trabajo asíncrono de generación de preguntas a partir de un archivo.
 * Guarda el texto extraído mientras se procesa y lo limpia al terminar.
 * El índice TTL elimina los jobs automáticamente a las 24 horas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "generation_jobs")
public class GenerationJob {

    @Id
    private String id;
    private Long userId;
    private Account role;
    private String topic;
    private JobStatus status;
    private int progress;
    private String extractedText;
    private List<GeneratedTopic> result;
    private String error;

    @Indexed(name = "generation_jobs_ttl", expireAfterSeconds = 86_400)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Transient
    public boolean isFinished() {
        return JobStatus.COMPLETED.equals(status) || JobStatus.FAILED.equals(status);
    }
}
