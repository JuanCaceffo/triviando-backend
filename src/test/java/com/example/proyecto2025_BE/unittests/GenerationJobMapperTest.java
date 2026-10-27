package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.model.GeneratedTopic;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.service.GenerationJobMapper;
import com.example.proyecto2025_BE.views.documents.GenerationJobCreatedResponse;
import com.example.proyecto2025_BE.views.documents.GenerationJobResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Generation Job Mapper Tests")
class GenerationJobMapperTest {

    private GenerationJobMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GenerationJobMapper();
    }

    @Test
    @DisplayName("Mapea un job recién creado")
    void mapeaJobCreado() {
        GenerationJob job = GenerationJob.builder()
                .id(1L)
                .topic("Biología")
                .status(JobStatus.PENDING)
                .progress(0)
                .build();

        GenerationJobCreatedResponse response = mapper.toCreatedResponse(job);

        assertEquals(1L, response.jobId());
        assertEquals("Biología", response.topic());
        assertEquals(JobStatus.PENDING, response.status());
        assertEquals(0, response.progress());
    }

    @Test
    @DisplayName("Mapea un job con resultado")
    void mapeaJobConResultado() {
        GenerationJob job = GenerationJob.builder()
                .id(1L)
                .topic("Biología")
                .status(JobStatus.COMPLETED)
                .progress(100)
                .result(List.of(GeneratedTopic.builder().topic("Biología").size(10).emoji("🧬").build()))
                .build();

        GenerationJobResponse response = mapper.toResponse(job);

        assertEquals(JobStatus.COMPLETED, response.status());
        assertEquals(1, response.result().size());
        assertEquals("Biología", response.result().getFirst().topic());
        assertEquals(10, response.result().getFirst().size());
        assertEquals("🧬", response.result().getFirst().emoji());
    }

    @Test
    @DisplayName("Un job sin resultado se mapea con result nulo")
    void mapeaJobSinResultado() {
        GenerationJob job = GenerationJob.builder()
                .id(1L)
                .topic("Biología")
                .status(JobStatus.PROCESSING)
                .progress(30)
                .build();

        GenerationJobResponse response = mapper.toResponse(job);

        assertNull(response.result());
        assertEquals(30, response.progress());
    }

    @Test
    @DisplayName("Convierte Topics del LLM en etiquetas embebidas")
    void convierteTopics() {
        List<GeneratedTopic> result = mapper.toGeneratedTopics(
                List.of(Topics.builder().topic("Biología").size(10).emoji("🧬").build()));

        assertEquals(1, result.size());
        assertEquals("Biología", result.getFirst().getTopic());
        assertEquals(10, result.getFirst().getSize());
    }

    @Test
    @DisplayName("Una lista nula se convierte en lista vacía")
    void convierteListaNula() {
        assertTrue(mapper.toGeneratedTopics(null).isEmpty());
    }
}
