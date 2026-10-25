package com.example.proyecto2025_BE.model;

/**
 * Fases del progreso de un job de generación desde archivo.
 * Centraliza los valores para que worker y servicio no usen números mágicos.
 */
public final class GenerationProgress {

    public static final int QUEUED = 0;
    public static final int STARTED = 5;
    public static final int CONTEXT_READY = 65;
    public static final int DONE = 100;

    private static final int SUMMARIZING_START = 10;
    private static final int SUMMARIZING_END = 60;

    private GenerationProgress() {
    }

    /**
     * Traduce el porcentaje de bloques resumidos (0-100) al tramo del progreso
     * total reservado para la fase de resumen.
     */
    public static int fromSummaryPercentage(int percentage) {
        int bounded = Math.max(0, Math.min(100, percentage));
        return SUMMARIZING_START + (bounded * (SUMMARIZING_END - SUMMARIZING_START) / 100);
    }
}
