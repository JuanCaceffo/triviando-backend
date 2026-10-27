package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Option;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.Topic;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.model.dto.llm.Question;
import com.example.proyecto2025_BE.model.dto.llm.QuestionList;
import com.example.proyecto2025_BE.model.dto.llm.QuestionOption;
import com.example.proyecto2025_BE.model.prompter.Prompter;
import com.example.proyecto2025_BE.repository.TopicRepository;
import com.example.proyecto2025_BE.utils.ContentNormalizer;
import dev.langchain4j.exception.LangChain4jException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMApiClientService {

    private static final String ERROR_MESSAGE = "Error in LLM response deserialization";

    private final ModelCommunication assistant;
    private final IQuestion IQuestion;
    private final QuestionDeduplicationService deduplicationService;
    private final TopicRepository topicRepository;

    @Transactional
    public List<Topics> generate(Prompter prompter, Account role, Long userId) {
        prompter.withService(IQuestion);
        prompter.setUserId(userId);
        prompter.validatePrompt();

        List<String> existingEnunciados = prompter.existingEnunciados();
        QuestionList response = requestQuestions(prompter.buildPrompt(existingEnunciados));

        List<Question> validQuestions = deduplicationService.removeDuplicates(response.questions(), existingEnunciados);

        if (validQuestions.size() < response.questions().size()) {
            validQuestions = requestQuestionsAvoidingDuplicates(prompter, existingEnunciados, validQuestions,
                    response.questions().size());
        }

        String emoji = prompter.getEmoji(assistant);
        return saveAndMappingResponse(validQuestions, prompter, emoji);
    }

    /**
     * Reintenta una sola vez la generación, pidiendo al LLM que evite las preguntas
     * existentes y las ya aceptadas de la primera tanda. Combina ambos lotes sin
     * superar la cantidad original solicitada.
     */
    private List<Question> requestQuestionsAvoidingDuplicates(Prompter prompter, List<String> existingEnunciados,
                                                              List<Question> firstBatch, int requestedQuantity) {
        // La primera tanda va primero para quedar siempre dentro del tope del prompt
        List<String> enunciadosToAvoid = new ArrayList<>();
        firstBatch.forEach(question -> enunciadosToAvoid.add(question.text()));
        enunciadosToAvoid.addAll(existingEnunciados);

        QuestionList retryResponse = requestQuestions(prompter.buildPromptAvoiding(enunciadosToAvoid));
        List<Question> retryValidQuestions = deduplicationService.removeDuplicates(retryResponse.questions(),
                enunciadosToAvoid);

        List<Question> merged = new ArrayList<>(firstBatch);
        retryValidQuestions.stream()
                .limit(Math.max(0, requestedQuantity - merged.size()))
                .forEach(merged::add);
        return merged;
    }

    private QuestionList requestQuestions(String promptMessage) {
        try {
            return assistant.generateQuestions(promptMessage);
        } catch (Exception e) {
            log.error(ERROR_MESSAGE, e);
            throw new LangChain4jException(ERROR_MESSAGE, e);
        }
    }

    private List<Topics> saveAndMappingResponse(List<Question> questions, Prompter prompter, String emoji) {
        Topic topic = resolveTopic(prompter, emoji);
        List<Pregunta> preguntas = questions.stream()
                .map(question -> toPregunta(question, topic))
                .toList();

        IQuestion.saveAll(preguntas);
        return IQuestion.contarPreguntasPorTopicoDeUsuario(prompter.getUserId());
    }

    private Topic resolveTopic(Prompter prompter, String emoji) {
        String normalized = ContentNormalizer.topicKey(prompter.getTopic());
        return topicRepository.findByUserIdAndNameNormalized(prompter.getUserId(), normalized)
                .orElseGet(() -> topicRepository.save(Topic.builder()
                        .userId(prompter.getUserId())
                        .name(ContentNormalizer.displayName(prompter.getTopic()))
                        .nameNormalized(normalized)
                        .emoji(emoji)
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    private Pregunta toPregunta(Question question, Topic topic) {
        Pregunta pregunta = Pregunta.builder()
                .topic(topic)
                .enunciado(question.text())
                .explicacion(question.briefExplanationOfTheCorrectAnswer())
                .difficulty(question.difficulty())
                .createdAt(LocalDateTime.now())
                .build();

        List<QuestionOption> allOptions = question.options() == null
                ? new ArrayList<>()
                : new ArrayList<>(question.options());

        // El LLM normalmente devuelve las 4 opciones juntas; si la correcta
        // viniera solo en correctOption, se agrega para no perderla.
        if (allOptions.stream().noneMatch(option -> option.letter().equals(question.correctOption().letter()))) {
            allOptions.add(question.correctOption());
        }

        List<Option> options = new ArrayList<>();
        for (int position = 0; position < allOptions.size(); position++) {
            QuestionOption option = allOptions.get(position);
            options.add(Option.builder()
                    .question(pregunta)
                    .letter(option.letter())
                    .text(option.text())
                    .correct(option.letter().equals(question.correctOption().letter()))
                    .positionOrder(position)
                    .build());
        }
        pregunta.setOptions(options);
        return pregunta;
    }
}
