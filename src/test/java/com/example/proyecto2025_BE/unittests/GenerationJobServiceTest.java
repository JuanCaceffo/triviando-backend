package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.GenerationProgress;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import com.example.proyecto2025_BE.service.GenerationJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Generation Job Service Tests")
class GenerationJobServiceTest {

    private GenerationJobRepository repository;
    private GenerationJobService service;

    @BeforeEach
    void setUp() {
        repository = mock(GenerationJobRepository.class);
        service = new GenerationJobService(repository, new DocumentProperties());
    }

    @Test
    @DisplayName("Crea un job pendiente con el texto extraído")
    void creaJob() {
        when(repository.save(any(GenerationJob.class))).thenAnswer(invocation -> {
            GenerationJob job = invocation.getArgument(0);
            job.setId("job1");
            return job;
        });

        GenerationJob job = service.create(1L, Account.FREE, "Biología", "texto extraído");

        assertEquals("job1", job.getId());
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(GenerationProgress.QUEUED, job.getProgress());
        assertEquals("texto extraído", job.getExtractedText());
        assertNotNull(job.getCreatedAt());
        assertNotNull(job.getUpdatedAt());
    }

    @Test
    @DisplayName("Devuelve un job propio y reciente sin modificarlo")
    void devuelveJobPropio() {
        GenerationJob job = GenerationJob.builder()
                .id("job1")
                .userId(1L)
                .status(JobStatus.PROCESSING)
                .updatedAt(LocalDateTime.now())
                .build();
        when(repository.findByIdAndUserId("job1", 1L)).thenReturn(Optional.of(job));

        GenerationJob result = service.getOwned("job1", 1L);

        assertEquals(job, result);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un job ajeno o inexistente devuelve 404")
    void jobInexistente() {
        when(repository.findByIdAndUserId("job1", 1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getOwned("job1", 1L));
    }

    @Test
    @DisplayName("Un job sin avance se marca como expirado")
    void marcaJobExpirado() {
        GenerationJob job = GenerationJob.builder()
                .id("job1")
                .userId(1L)
                .status(JobStatus.PROCESSING)
                .progress(30)
                .extractedText("texto")
                .updatedAt(LocalDateTime.now().minusMinutes(30))
                .build();
        when(repository.findByIdAndUserId("job1", 1L)).thenReturn(Optional.of(job));

        GenerationJob result = service.getOwned("job1", 1L);

        assertEquals(JobStatus.FAILED, result.getStatus());
        assertTrue(result.getError().contains("expiró"));
        assertNull(result.getExtractedText());
        verify(repository).save(job);
    }
}
