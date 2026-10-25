package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.constants.LLM;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Modelo auxiliar para resumir documentos. Tiene su propio system prompt
 * para no mezclarse con el generador de preguntas.
 */
public interface DocumentSummarizer {

    @SystemMessage(LLM.DOCUMENT_SUMMARIZER_SYSTEM_PROMPT)
    @UserMessage(LLM.TEMPLATE_DOCUMENT_SUMMARY_PROMPT)
    String summarizeFragment(String fragment);

    @SystemMessage(LLM.DOCUMENT_SUMMARIZER_SYSTEM_PROMPT)
    @UserMessage(LLM.TEMPLATE_DOCUMENT_REDUCE_PROMPT)
    String reduceSummaries(String summaries);
}
