package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import com.example.proyecto2025_BE.service.GenerationJobCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Generation Job Cleanup Tests")
class GenerationJobCleanupServiceTest {

    @Mock
    private GenerationJobRepository repository;

    @InjectMocks
    private GenerationJobCleanupService service;

    @Test
    @DisplayName("Borra jobs con más de 24 horas")
    void borraJobsViejos() {
        when(repository.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(3);

        service.cleanup();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(cutoff.capture());

        LocalDateTime now = LocalDateTime.now();
        assertTrue(cutoff.getValue().isBefore(now.minusHours(23)));
        assertTrue(cutoff.getValue().isAfter(now.minusHours(25)));
    }
}
