package com.example.proyecto2025_BE.model.prompter;

import com.example.proyecto2025_BE.constants.LLM;

/**
 * Prompter para crear un tópico nuevo a partir del contenido de un archivo.
 * Hereda de TopicPrompter la validación de tópico duplicado, el límite diario
 * y la generación del emoji; solo cambia el prompt base para inyectar el
 * contenido de referencia ya resumido y acotado.
 * <p>
 * Se construye únicamente con {@link #forDocument(String, Long, String)} para
 * garantizar que siempre tenga contexto. No está registrado en {@link Prompter}
 * para deserialización JSON: solo el backend lo crea con texto validado.
 */
public class FilePrompter extends TopicPrompter {

    private final String summarizedContext;

    private FilePrompter(String topic, Long userId, String summarizedContext) {
        this.topic = topic;
        this.userId = userId;
        this.summarizedContext = summarizedContext;
    }

    public static FilePrompter forDocument(String topic, Long userId, String summarizedContext) {
        if (summarizedContext == null || summarizedContext.isBlank()) {
            throw new IllegalArgumentException("El contexto resumido del documento es obligatorio");
        }
        return new FilePrompter(topic, userId, summarizedContext);
    }

    @Override
    protected String basePrompt() {
        return String.format(LLM.TEMPLATE_FILE_PROMPT, questionsQuantity(), topic.toLowerCase(), summarizedContext);
    }
}
