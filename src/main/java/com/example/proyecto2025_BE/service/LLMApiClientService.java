package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Option;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.model.dto.llm.Question;
import com.example.proyecto2025_BE.model.dto.llm.QuestionList;
import com.example.proyecto2025_BE.model.dto.llm.QuestionOption;
import com.example.proyecto2025_BE.model.prompter.Prompter;
import dev.langchain4j.exception.LangChain4jException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    @Transactional
    public List<Topics> generate(Prompter prompter, Account role, Long userId) {
        prompter.withService(IQuestion);
        prompter.setUserId(userId);
        prompter.validatePrompt();

        if (Account.FREE.equals(role)) {
            prompter.validateGeneration(userId);
        }

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

    //TODO: evaluar la posibilidad de utilizar el strategy para realizar el parseo y el guardado de los datos para darle mas versatilidad a la integración
    private List<Topics> saveAndMappingResponse(List<Question> questions, Prompter prompter, String emoji) {

        List<Pregunta> preguntas = questions.stream().map(question ->
                        Pregunta.builder()
                                .topico(prompter.getTopic())
                                .userId(prompter.getUserId())
                                .emoji(emoji)
                                .enunciado(question.text())
                                .options(buildIncorrectOptions(question.options()))
                                .correctOption(buildCorrectOption(question.correctOption()))
                                .explicacion(question.briefExplanationOfTheCorrectAnswer())
                                .difficulty(question.difficulty())
                                .build())
                .toList();
        IQuestion.saveAll(preguntas);

        return IQuestion.contarPreguntasPorTopicoDeUsuario(prompter.getUserId());
    }

    private Option buildCorrectOption(QuestionOption correctOption) {
        return Option.builder()
                .text(correctOption.text())
                .letter(correctOption.letter())
                .build();
    }

    private List<Option> buildIncorrectOptions(List<QuestionOption> incorrectOptions) {
        return incorrectOptions.stream().map(option ->
                        Option.builder()
                                .text(option.text())
                                .letter(option.letter())
                                .build())
                .toList();
    }
}
