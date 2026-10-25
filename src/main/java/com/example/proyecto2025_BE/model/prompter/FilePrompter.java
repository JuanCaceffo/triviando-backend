package com.example.proyecto2025_BE.model.prompter;

import com.example.proyecto2025_BE.constants.LLM;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Prompter para crear un tópico nuevo a partir del contenido de un archivo.
 * Hereda de TopicPrompter la validación de tópico duplicado, el límite diario
 * y la generación del emoji; solo cambia el prompt base para inyectar el
 * contenido de referencia ya resumido y acotado.
 * <p>
 * No está registrado en {@link Prompter} para deserialización JSON: solo el
 * backend lo construye con un contexto validado.
 */
@SuperBuilder
@NoArgsConstructor
public class FilePrompter extends TopicPrompter {

    private String context;

    @Override
    protected String basePrompt() {
        String safeContext = context == null ? "" : context;
        return String.format(LLM.TEMPLATE_FILE_PROMPT, questionsQuantity(), topic.toLowerCase(), safeContext);
    }
}
