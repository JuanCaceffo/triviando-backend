package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import com.example.proyecto2025_BE.service.GenerationRequestPolicy;
import com.example.proyecto2025_BE.service.IQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Generation Request Policy Tests")
class GenerationRequestPolicyTest {

    private GenerationJobRepository repository;
    private IQuestion questionService;
    private DocumentProperties properties;
    private GenerationRequestPolicy policy;

    @BeforeEach
    void setUp() {
        repository = mock(GenerationJobRepository.class);
        questionService = mock(IQuestion.class);
        properties = new DocumentProperties();
        policy = new GenerationRequestPolicy(repository, properties, questionService);
    }

    @Test
    @DisplayName("Rechaza un tópico vacío sin consultar nada")
    void rechazaTopicoVacio() {
        assertThrows(ValidationException.class,
                () -> policy.validateRequest("  ", Account.FREE, 1L));

        verifyNoInteractions(repository, questionService);
    }

    @Test
    @DisplayName("Normaliza el tópico quitando espacios")
    void normalizaTopico() {
        when(repository.findFirstByUserIdAndStatusIn(anyLong(), any())).thenReturn(Optional.empty());
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(false);
        when(questionService.canCreateTopic(eq(1L), any(), any())).thenReturn(true);

        String topic = policy.validateRequest("  Biología  ", Account.FREE, 1L);

        assertEquals("Biología", topic);
        verify(questionService).existsByTopicAndUser("Biología", 1L);
    }

    @Test
    @DisplayName("Rechaza si el usuario ya tiene un job activo")
    void rechazaJobActivo() {
        when(repository.findFirstByUserIdAndStatusIn(eq(1L), any()))
                .thenReturn(Optional.of(GenerationJob.builder().id("otro").build()));

        assertThrows(ConflictException.class,
                () -> policy.validateRequest("Biología", Account.FREE, 1L));

        verify(questionService, never()).existsByTopicAndUser(any(), anyLong());
    }

    @Test
    @DisplayName("Rechaza un tópico que ya existe")
    void rechazaTopicoExistente() {
        when(repository.findFirstByUserIdAndStatusIn(anyLong(), any())).thenReturn(Optional.empty());
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> policy.validateRequest("Biología", Account.FREE, 1L));
    }

    @Test
    @DisplayName("Aplica el límite diario a los usuarios FREE")
    void aplicaLimiteFree() {
        when(repository.findFirstByUserIdAndStatusIn(anyLong(), any())).thenReturn(Optional.empty());
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(false);
        when(questionService.canCreateTopic(eq(1L), any(), any())).thenReturn(false);

        assertThrows(ValidationException.class,
                () -> policy.validateRequest("Biología", Account.FREE, 1L));
    }

    @Test
    @DisplayName("Los usuarios PREMIUM no consultan el cupo diario")
    void premiumNoConsultaCupo() {
        when(repository.findFirstByUserIdAndStatusIn(anyLong(), any())).thenReturn(Optional.empty());
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(false);

        policy.validateRequest("Biología", Account.PREMIUM, 1L);

        verify(questionService, never()).canCreateTopic(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Si la política de job único está desactivada no consulta jobs activos")
    void jobUnicoDesactivado() {
        properties.setOneActiveJobPerUser(false);
        when(questionService.existsByTopicAndUser("Biología", 1L)).thenReturn(false);
        when(questionService.canCreateTopic(eq(1L), any(), any())).thenReturn(true);

        policy.validateRequest("Biología", Account.FREE, 1L);

        verify(repository, never()).findFirstByUserIdAndStatusIn(anyLong(), any());
    }
}
