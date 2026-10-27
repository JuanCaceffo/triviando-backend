package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.GeneratedTopic;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.service.DocumentGenerationService;
import com.example.proyecto2025_BE.service.DocumentGenerationWorker;
import com.example.proyecto2025_BE.service.GenerationJobMapper;
import com.example.proyecto2025_BE.service.GenerationJobService;
import com.example.proyecto2025_BE.service.GenerationRequestPolicy;
import com.example.proyecto2025_BE.service.document.DocumentTextExtractionService;
import com.example.proyecto2025_BE.views.documents.GenerationJobCreatedResponse;
import com.example.proyecto2025_BE.views.documents.GenerationJobResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Document Generation Service Tests")
class DocumentGenerationServiceTest {

    private GenerationRequestPolicy policy;
    private DocumentTextExtractionService textExtractionService;
    private GenerationJobService jobService;
    private DocumentGenerationWorker worker;
    private DocumentGenerationService service;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        policy = mock(GenerationRequestPolicy.class);
        textExtractionService = mock(DocumentTextExtractionService.class);
        jobService = mock(GenerationJobService.class);
        worker = mock(DocumentGenerationWorker.class);
        service = new DocumentGenerationService(policy, textExtractionService, jobService,
                new GenerationJobMapper(), worker);

        file = new MockMultipartFile("file", "apunte.txt", "text/plain", "contenido".getBytes());
    }

    @Test
    @DisplayName("Valida, extrae, crea el job y lo encola")
    void encolaJobExitosamente() {
        GenerationJob job = GenerationJob.builder()
                .id("job1")
                .topic("Biología")
                .status(JobStatus.PENDING)
                .progress(0)
                .build();
        when(policy.validateRequest("Biología", Account.PREMIUM, 1L)).thenReturn("Biología");
        when(textExtractionService.extract(file)).thenReturn("texto extraído");
        when(jobService.create(1L, Account.PREMIUM, "Biología", "texto extraído")).thenReturn(job);

        GenerationJobCreatedResponse response = service.enqueue("Biología", file, Account.PREMIUM, 1L);

        assertEquals("job1", response.jobId());
        assertEquals("Biología", response.topic());
        assertEquals(JobStatus.PENDING, response.status());
        verify(worker).process("job1");
    }

    @Test
    @DisplayName("No extrae ni crea nada si la política rechaza la solicitud")
    void noAvanzaSiLaPoliticaRechaza() {
        when(policy.validateRequest(any(), any(), any())).thenThrow(ConflictException.build("existe"));

        assertThrows(ConflictException.class,
                () -> service.enqueue("Biología", file, Account.FREE, 1L));

        verifyNoInteractions(textExtractionService, jobService, worker);
    }

    @Test
    @DisplayName("Consulta un job propio a través del servicio de jobs")
    void consultaJob() {
        GenerationJob job = GenerationJob.builder()
                .id("job1")
                .topic("Biología")
                .status(JobStatus.COMPLETED)
                .progress(100)
                .result(List.of(GeneratedTopic.builder().topic("Biología").size(10).emoji("🧬").build()))
                .build();
        when(jobService.getOwned("job1", 1L)).thenReturn(job);

        GenerationJobResponse response = service.getJob("job1", 1L);

        assertEquals(JobStatus.COMPLETED, response.status());
        assertEquals("🧬", response.result().getFirst().emoji());
    }

    @Test
    @DisplayName("Propaga el 404 de un job ajeno o inexistente")
    void propagaJobInexistente() {
        when(jobService.getOwned(eq("job1"), eq(1L)))
                .thenThrow(NotFoundException.build("no existe"));

        assertThrows(NotFoundException.class, () -> service.getJob("job1", 1L));
    }
}
