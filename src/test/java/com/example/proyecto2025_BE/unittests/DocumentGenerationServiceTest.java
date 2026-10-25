package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.GeneratedTopic;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import com.example.proyecto2025_BE.service.DocumentGenerationService;
import com.example.proyecto2025_BE.service.DocumentGenerationWorker;
import com.example.proyecto2025_BE.service.DocumentTextExtractor;
import com.example.proyecto2025_BE.service.IQuestion;
import com.example.proyecto2025_BE.views.documents.GenerationJobCreatedResponse;
import com.example.proyecto2025_BE.views.documents.GenerationJobResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Document Generation Service Tests")
class DocumentGenerationServiceTest {

    private GenerationJobRepository repository;
    private DocumentTextExtractor textExtractor;
    private DocumentGenerationWorker worker;
    private IQuestion questionService;
    private DocumentGenerationService service;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        repository = mock(GenerationJobRepository.class);
        textExtractor = mock(DocumentTextExtractor.class);
        worker = mock(DocumentGenerationWorker.class);
        questionService = mock(IQuestion.class);
        service = new DocumentGenerationService(repository, textExtractor, worker,
                new DocumentProperties(), questionService);

        file = new MockMultipartFile("file", "apunte.txt", "text/plain", "contenido".getBytes());
    }

    @Test
    @DisplayName("Encola un job nuevo y dispara el worker")
    void encolaJobExitosamente() {
        when(textExtractor.extract(file)).thenReturn("texto extraído");
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(false);
        when(questionService.canCreateTopic(eq(1L), any(), any())).thenReturn(true);
        when(repository.save(any(GenerationJob.class))).thenAnswer(invocation -> {
            GenerationJob job = invocation.getArgument(0);
            job.setId("job1");
            return job;
        });

        GenerationJobCreatedResponse response = service.enqueue("Biología", file, Account.FREE, 1L);

        assertEquals("job1", response.jobId());
        assertEquals("Biología", response.topic());
        assertEquals(JobStatus.PENDING, response.status());
        verify(worker).process("job1");
    }

    @Test
    @DisplayName("Rechaza un tópico vacío")
    void rechazaTopicoVacio() {
        assertThrows(ValidationException.class, () -> service.enqueue("  ", file, Account.FREE, 1L));
        verify(textExtractor, never()).extract(any());
    }

    @Test
    @DisplayName("Rechaza si el usuario ya tiene una generación en curso")
    void rechazaJobActivo() {
        when(repository.findFirstByUserIdAndStatusIn(eq(1L), any()))
                .thenReturn(Optional.of(GenerationJob.builder().id("otro").build()));

        assertThrows(ConflictException.class, () -> service.enqueue("Biología", file, Account.FREE, 1L));
        verify(textExtractor, never()).extract(any());
    }

    @Test
    @DisplayName("Rechaza un tópico que ya existe")
    void rechazaTopicoExistente() {
        when(repository.findFirstByUserIdAndStatusIn(eq(1L), any())).thenReturn(Optional.empty());
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.enqueue("Biología", file, Account.FREE, 1L));
        verify(textExtractor, never()).extract(any());
    }

    @Test
    @DisplayName("Aplica el límite diario a los usuarios FREE")
    void aplicaLimiteFree() {
        when(repository.findFirstByUserIdAndStatusIn(eq(1L), any())).thenReturn(Optional.empty());
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(false);
        when(questionService.canCreateTopic(eq(1L), any(), any())).thenReturn(false);

        assertThrows(ValidationException.class, () -> service.enqueue("Biología", file, Account.FREE, 1L));
        verify(textExtractor, never()).extract(any());
    }

    @Test
    @DisplayName("Los usuarios PREMIUM no tienen límite diario")
    void premiumNoTieneLimite() {
        when(repository.findFirstByUserIdAndStatusIn(eq(1L), any())).thenReturn(Optional.empty());
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(false);
        when(textExtractor.extract(file)).thenReturn("texto");
        when(repository.save(any(GenerationJob.class))).thenAnswer(invocation -> {
            GenerationJob job = invocation.getArgument(0);
            job.setId("job1");
            return job;
        });

        service.enqueue("Biología", file, Account.PREMIUM, 1L);

        verify(questionService, never()).canCreateTopic(anyLong(), any(), any());
        verify(worker).process("job1");
    }

    @Test
    @DisplayName("Devuelve el estado y el resultado de un job propio")
    void devuelveJobPropio() {
        GenerationJob job = GenerationJob.builder()
                .id("job1")
                .userId(1L)
                .topic("Biología")
                .status(JobStatus.COMPLETED)
                .progress(100)
                .result(List.of(GeneratedTopic.builder().topic("Biología").size(10).emoji("🧬").build()))
                .updatedAt(LocalDateTime.now())
                .build();
        when(repository.findByIdAndUserId("job1", 1L)).thenReturn(Optional.of(job));

        GenerationJobResponse response = service.getJob("job1", 1L);

        assertEquals(JobStatus.COMPLETED, response.status());
        assertEquals(1, response.result().size());
        assertEquals("Biología", response.result().getFirst().topic());
        assertEquals(10, response.result().getFirst().size());
        assertEquals("🧬", response.result().getFirst().emoji());
    }

    @Test
    @DisplayName("Un job ajeno o inexistente devuelve 404")
    void jobAjenoNoSeEncuentra() {
        when(repository.findByIdAndUserId("job1", 1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getJob("job1", 1L));
    }

    @Test
    @DisplayName("Un job sin avance se marca como expirado")
    void marcaJobExpirado() {
        GenerationJob job = GenerationJob.builder()
                .id("job1")
                .userId(1L)
                .topic("Biología")
                .status(JobStatus.PROCESSING)
                .progress(30)
                .contextText("texto")
                .updatedAt(LocalDateTime.now().minusMinutes(30))
                .build();
        when(repository.findByIdAndUserId("job1", 1L)).thenReturn(Optional.of(job));

        GenerationJobResponse response = service.getJob("job1", 1L);

        assertEquals(JobStatus.FAILED, response.status());
        assertNotNull(response.error());
        assertTrue(response.error().contains("expiró"));
        verify(repository).save(job);
    }
}
