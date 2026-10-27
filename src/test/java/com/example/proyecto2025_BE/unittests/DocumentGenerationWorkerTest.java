package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.model.prompter.FilePrompter;
import com.example.proyecto2025_BE.model.prompter.Prompter;
import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import com.example.proyecto2025_BE.service.DocumentContextBuilder;
import com.example.proyecto2025_BE.service.DocumentGenerationWorker;
import com.example.proyecto2025_BE.service.GenerationJobMapper;
import com.example.proyecto2025_BE.service.LLMApiClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Document Generation Worker Tests")
class DocumentGenerationWorkerTest {

    private GenerationJobRepository repository;
    private DocumentContextBuilder contextBuilder;
    private LLMApiClientService llmApiClientService;
    private DocumentGenerationWorker worker;

    @BeforeEach
    void setUp() {
        repository = mock(GenerationJobRepository.class);
        contextBuilder = mock(DocumentContextBuilder.class);
        llmApiClientService = mock(LLMApiClientService.class);
        worker = new DocumentGenerationWorker(repository, contextBuilder, llmApiClientService,
                new GenerationJobMapper());
    }

    @Test
    @DisplayName("Procesa el job, guarda el resultado y lo marca como completado")
    void procesaJobExitosamente() {
        GenerationJob job = crearJob(JobStatus.PENDING, "texto crudo");
        when(repository.findById(1L)).thenReturn(Optional.of(job));
        when(contextBuilder.buildContext(anyString(), any())).thenReturn("contexto resumido");
        when(llmApiClientService.generate(any(FilePrompter.class), eq(Account.FREE), eq(1L)))
                .thenReturn(List.of(Topics.builder().topic("Biología").size(10).emoji("🧬").build()));

        worker.process(1L);

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getProgress());
        assertEquals(1, job.getResult().size());
        assertEquals("Biología", job.getResult().getFirst().getTopic());
        assertEquals(10, job.getResult().getFirst().getSize());
        assertNull(job.getExtractedText());

        ArgumentCaptor<Prompter> prompter = ArgumentCaptor.forClass(Prompter.class);
        verify(llmApiClientService).generate(prompter.capture(), eq(Account.FREE), eq(1L));
        FilePrompter filePrompter = assertInstanceOf(FilePrompter.class, prompter.getValue());
        assertTrue(filePrompter.buildPrompt(List.of()).contains("contexto resumido"));
    }

    @Test
    @DisplayName("Si el resumen falla marca el job como fallido con mensaje genérico")
    void fallaDuranteElResumen() {
        GenerationJob job = crearJob(JobStatus.PENDING, "texto crudo");
        when(repository.findById(1L)).thenReturn(Optional.of(job));
        when(contextBuilder.buildContext(anyString(), any())).thenThrow(new IllegalStateException("boom"));

        worker.process(1L);

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals(Exceptions.GENERATION_FAILED, job.getError());
        assertNull(job.getExtractedText());
    }

    @Test
    @DisplayName("Un error de validación conserva su mensaje para el usuario")
    void fallaEnLaGeneracionConMensajeDeValidacion() {
        GenerationJob job = crearJob(JobStatus.PENDING, "texto crudo");
        when(repository.findById(1L)).thenReturn(Optional.of(job));
        when(contextBuilder.buildContext(anyString(), any())).thenReturn("contexto");
        when(llmApiClientService.generate(any(FilePrompter.class), any(), any()))
                .thenThrow(ValidationException.build("El tópico ya no está disponible"));

        worker.process(1L);

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("El tópico ya no está disponible", job.getError());
    }

    @Test
    @DisplayName("Ignora un job que ya no está pendiente")
    void ignoraJobNoPendiente() {
        GenerationJob job = crearJob(JobStatus.COMPLETED, "texto crudo");
        when(repository.findById(1L)).thenReturn(Optional.of(job));

        worker.process(1L);

        verifyNoInteractions(contextBuilder, llmApiClientService);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Ignora un job inexistente")
    void ignoraJobInexistente() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        worker.process(1L);

        verifyNoInteractions(contextBuilder, llmApiClientService);
    }

    private GenerationJob crearJob(JobStatus status, String extractedText) {
        return GenerationJob.builder()
                .id(1L)
                .userId(1L)
                .role(Account.FREE)
                .topic("Biología")
                .status(status)
                .progress(0)
                .extractedText(extractedText)
                .build();
    }
}
