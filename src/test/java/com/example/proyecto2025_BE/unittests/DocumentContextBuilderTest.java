package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import com.example.proyecto2025_BE.service.DocumentContextBuilder;
import com.example.proyecto2025_BE.service.DocumentSummarizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Document Context Builder Tests")
class DocumentContextBuilderTest {

    private DocumentProperties properties;
    private DocumentSummarizer summarizer;
    private DocumentContextBuilder builder;

    @BeforeEach
    void setUp() {
        properties = new DocumentProperties();
        summarizer = mock(DocumentSummarizer.class);
        Executor directExecutor = Runnable::run;
        builder = new DocumentContextBuilder(summarizer, properties, directExecutor);
    }

    @Test
    @DisplayName("Un documento corto se usa tal cual sin llamar al resumen")
    void documentoCortoNoSeResume() {
        properties.setPromptBudgetChars(100);
        String text = "Contenido corto sobre células y tejidos.";

        String context = builder.buildContext(text, null);

        assertEquals(text, context);
        verify(summarizer, never()).summarizeFragment(anyString());
    }

    @Test
    @DisplayName("Un documento largo se divide en bloques distribuidos y se resume")
    void documentoLargoSeResumePorBloques() {
        properties.setPromptBudgetChars(100);
        properties.setChunkSizeChars(50);
        properties.setMaxChunks(3);
        String text = "x".repeat(400);
        when(summarizer.summarizeFragment(anyString())).thenReturn("resumen");

        AtomicInteger lastProgress = new AtomicInteger(-1);
        String context = builder.buildContext(text, lastProgress::set);

        assertEquals("resumen\n\nresumen\n\nresumen", context);
        assertEquals(100, lastProgress.get());

        ArgumentCaptor<String> fragments = ArgumentCaptor.forClass(String.class);
        verify(summarizer, times(3)).summarizeFragment(fragments.capture());
        assertTrue(fragments.getAllValues().stream().allMatch(fragment -> fragment.length() <= 50));
    }

    @Test
    @DisplayName("Si los resúmenes superan el presupuesto se aplica un reduce final")
    void resumenesLargosSeReducen() {
        properties.setPromptBudgetChars(20);
        properties.setChunkSizeChars(10);
        properties.setMaxChunks(2);
        String text = "y".repeat(100);
        when(summarizer.summarizeFragment(anyString())).thenReturn("resumen extenso");
        when(summarizer.reduceSummaries(anyString())).thenReturn("final");

        String context = builder.buildContext(text, null);

        assertEquals("final", context);
        verify(summarizer).reduceSummaries(anyString());
    }

    @Test
    @DisplayName("Si el resumen queda vacío se usa el texto truncado como fallback")
    void resumenVacioUsaTextoTruncado() {
        properties.setPromptBudgetChars(50);
        properties.setChunkSizeChars(10);
        properties.setMaxChunks(2);
        String text = "z".repeat(100);
        when(summarizer.summarizeFragment(anyString())).thenReturn("   ");

        String context = builder.buildContext(text, null);

        assertEquals(50, context.length());
        assertTrue(context.startsWith("zzzz"));
    }

    @Test
    @DisplayName("Si el reduce falla no se propaga la excepción y se acota el resultado")
    void reduceFallidoNoPropagaLaExcepcion() {
        properties.setPromptBudgetChars(10);
        properties.setChunkSizeChars(10);
        properties.setMaxChunks(2);
        String text = "w".repeat(100);
        when(summarizer.summarizeFragment(anyString())).thenReturn("resumen");
        when(summarizer.reduceSummaries(anyString())).thenThrow(new IllegalStateException("boom"));

        String context = builder.buildContext(text, null);

        verify(summarizer).reduceSummaries(anyString());
        assertTrue(context.length() <= 10);
        assertTrue(context.startsWith("resumen"));
    }
}
