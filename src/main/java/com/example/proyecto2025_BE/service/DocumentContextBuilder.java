package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.configuration.DocumentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Convierte el texto extraído en un contexto que entra en la ventana del LLM.
 * Si el documento es corto se usa tal cual; si es largo se aplica un
 * map-reduce: se muestrean bloques distribuidos a lo largo de todo el texto,
 * se resume cada uno y, si hace falta, se unifican los resúmenes.
 */
@Slf4j
@Service
public class DocumentContextBuilder {

    private final DocumentSummarizer summarizer;
    private final DocumentProperties properties;
    private final Executor summaryExecutor;

    public DocumentContextBuilder(DocumentSummarizer summarizer,
                                  DocumentProperties properties,
                                  @Qualifier("documentSummaryExecutor") Executor summaryExecutor) {
        this.summarizer = summarizer;
        this.properties = properties;
        this.summaryExecutor = summaryExecutor;
    }

    /**
     * @param text          texto ya extraído y normalizado
     * @param chunkProgress callback opcional con el porcentaje de bloques resumidos (0-100)
     */
    public String buildContext(String text, IntConsumer chunkProgress) {
        IntConsumer progress = chunkProgress == null ? percentage -> { } : chunkProgress;
        String source = text == null ? "" : text.trim();
        int budget = Math.max(1, properties.getPromptBudgetChars());

        if (source.length() <= budget) {
            progress.accept(100);
            return source;
        }

        int chunkSize = Math.max(1, properties.getChunkSizeChars());
        int maxChunks = Math.max(1, properties.getMaxChunks());
        int chunkCount = Math.min(maxChunks,
                Math.max(1, (int) Math.ceil((double) source.length() / chunkSize)));

        AtomicInteger completed = new AtomicInteger();
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (int i = 0; i < chunkCount; i++) {
            int start = (int) ((long) source.length() * i / chunkCount);
            int end = Math.min(start + chunkSize, source.length());
            String fragment = source.substring(start, end);

            futures.add(CompletableFuture
                    .supplyAsync(() -> summarize(fragment), summaryExecutor)
                    .thenApply(summary -> {
                        int done = completed.incrementAndGet();
                        progress.accept(done * 100 / chunkCount);
                        return summary;
                    }));
        }

        String joined = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(summary -> !summary.isBlank())
                .collect(Collectors.joining("\n\n"));

        // Si el modelo no devolvió nada útil, al menos enviamos el comienzo del documento
        if (joined.isBlank()) {
            log.warn("El resumen del documento quedó vacío; se usa el texto truncado");
            return truncate(source, budget);
        }

        if (joined.length() > budget) {
            joined = reduce(joined);
        }

        return truncate(joined, budget);
    }

    private String summarize(String fragment) {
        try {
            return summarizer.summarizeFragment(fragment);
        } catch (Exception e) {
            log.error("Falló el resumen de un fragmento del documento", e);
            throw e;
        }
    }

    private String reduce(String summaries) {
        try {
            String reduced = summarizer.reduceSummaries(summaries);
            return reduced == null || reduced.isBlank() ? summaries : reduced;
        } catch (Exception e) {
            log.error("Falló el resumen final del documento; se usa la unión de resúmenes", e);
            return summaries;
        }
    }

    private String truncate(String text, int budget) {
        return text.length() > budget ? text.substring(0, budget) : text;
    }
}
