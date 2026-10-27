package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.Answer;
import com.example.proyecto2025_BE.model.FeedbackAnswer;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.repository.ResponseRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private static final String ALREADY_ANSWERED = "Esta pregunta ya ha sido respondida";

    private final UserService userService;
    private final IQuestion IQuestion;
    private final ResponseRepository answerRepository;

    @Transactional
    public FeedbackAnswer answer(Answer answer) {
        User user = userService.retrieve(answer.getUser().getId());

        Answer answered = answerRepository.findByUserIdAndQuestionId(user.getId(), answer.getQuestionId());
        if (answered != null) {
            throw new ValidationException(ALREADY_ANSWERED);
        }

        Pregunta question = IQuestion.getPreguntaById(answer.getQuestionId());
        answer.setUser(user);
        answer.setResponseDate(LocalDate.now());
        answer.impactScore(question);

        try {
            answerRepository.save(answer);
        } catch (DataIntegrityViolationException duplicate) {
            // El unique (user_id, question_id) cubre la carrera entre dos requests.
            throw new ValidationException(ALREADY_ANSWERED);
        }

        user.actualizarRacha();
        userService.update(user);

        return FeedbackAnswer.builder()
                .score(answer.getScore())
                .explanation(question.getExplicacion())
                .errorReason(answer.getErrorReason())
                .correctOption(question.getCorrectOption())
                .build();
    }
}
