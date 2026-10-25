package com.example.proyecto2025_BE.model.prompter;

import com.example.proyecto2025_BE.service.IQuestion;
import com.example.proyecto2025_BE.service.ModelCommunication;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "promptType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = QuestionPrompter.class, name = "questionPrompter"),
        @JsonSubTypes.Type(value = TopicPrompter.class, name = "topicPrompter")
})
public abstract class Prompter {

    private static final String TEMPLATE_PROMPT = "Generame %s preguntas cortas sobre %s";
    private static final String TEMPLATE_PROMPT_WITH_CONTEXT =
            "%s. Ya existen estas preguntas sobre el topico, no las repitas ni las parafrasees: %s";
    private static final String TEMPLATE_PROMPT_AVOIDING =
            "%s. Asegurate de que ninguna de las preguntas generadas coincida ni parafrasee ninguna de estas: %s";
    protected static final int MAX_PROMPT_QUESTIONS = 30;
    private static final int MAX_ENUNCIADO_LENGTH = 120;

    @NotBlank
    protected String topic;
    protected Long userId;
    protected IQuestion question;

    public String buildPrompt(List<String> existingEnunciados) {
        String basePrompt = basePrompt();
        if (existingEnunciados.isEmpty()) {
            return basePrompt;
        }
        String context = joinEnunciados(limitTo(existingEnunciados));
        return String.format(TEMPLATE_PROMPT_WITH_CONTEXT, basePrompt, context);
    }

    public String buildPromptAvoiding(List<String> enunciadosToAvoid) {
        // Sin seccion de contexto: la lista a evitar ya subsume las preguntas existentes
        String avoidList = joinEnunciados(limitTo(enunciadosToAvoid));
        return String.format(TEMPLATE_PROMPT_AVOIDING, basePrompt(), avoidList);
    }

    public List<String> existingEnunciados() {
        return List.of();
    }

    protected String basePrompt() {
        return String.format(TEMPLATE_PROMPT, questionsQuantity(), topic.toLowerCase());
    }

    // Todo lo que viaja al LLM se acota: el dedup en memoria ve la lista completa, el prompt no
    private List<String> limitTo(List<String> enunciados) {
        return enunciados.stream().limit(MAX_PROMPT_QUESTIONS).toList();
    }

    private String joinEnunciados(List<String> enunciados) {
        return enunciados.stream()
                .filter(entrada -> entrada != null && !entrada.isBlank())
                .map(entrada -> entrada.length() > MAX_ENUNCIADO_LENGTH
                        ? entrada.substring(0, MAX_ENUNCIADO_LENGTH) + "..."
                        : entrada)
                .collect(Collectors.joining(" | "));
    }

    protected abstract String questionsQuantity();

    public abstract void validatePrompt();

    public Prompter withService(IQuestion IQuestion) {
        this.question = IQuestion;
        return this;
    }

    public abstract String getEmoji(ModelCommunication assistant);

    public boolean existsTopic() {
        return question.existsByTopicAndUser(topic, userId);
    }

    public abstract void validateGeneration(Long userId);
}
