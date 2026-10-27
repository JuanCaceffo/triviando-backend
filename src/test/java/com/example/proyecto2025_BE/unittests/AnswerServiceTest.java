package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.Answer;
import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.FeedbackAnswer;
import com.example.proyecto2025_BE.model.LetterOption;
import com.example.proyecto2025_BE.model.Option;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.repository.ResponseRepository;
import com.example.proyecto2025_BE.service.AnswerService;
import com.example.proyecto2025_BE.service.IQuestion;
import com.example.proyecto2025_BE.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Answer Service Test")
public class AnswerServiceTest {

    private static AnswerService answerService;
    private static Option correctOption;
    private static Answer.AnswerBuilder answerBuilder;
    private static ResponseRepository answerRepository;
    private static User user;

    @BeforeAll
    static void beforeAll() {
        UserService userServiceMock = mock(UserService.class);
        IQuestion questionServiceMock = mock(IQuestion.class);
        answerRepository = mock(ResponseRepository.class);
        answerService = new AnswerService(userServiceMock, questionServiceMock, answerRepository);

        user = User.builder().id(1L).build();

        correctOption = Option.builder()
                .letter(LetterOption.A)
                .text("Correcta")
                .correct(true)
                .build();

        Pregunta pregunta = Pregunta.builder()
                .options(List.of(correctOption))
                .difficulty(Difficulty.MEDIUM)
                .build();

        answerBuilder = Answer.builder()
                .questionId(10L)
                .user(user);

        when(userServiceMock.retrieve(anyLong())).thenReturn(user);
        when(questionServiceMock.getPreguntaById(anyLong())).thenReturn(pregunta);
    }

    @Test
    @DisplayName("Success answer without latency penalty limit, without unsuccessful limit")
    void answerTest() {
        Answer answer = answerBuilder.millisecondsSpent(29999)
                .optionSelected(correctOption.getLetter())
                .build();

        FeedbackAnswer feedback = answerService.answer(answer);

        assertEquals(BigDecimal.valueOf(16), feedback.getScore());
    }

    @Test
    @DisplayName("Success answer with latency penalty limit, without unsuccessful limit")
    void answerWithLatencyPenaltyLimitTest() {
        Answer answer = answerBuilder.millisecondsSpent(30001)
                .optionSelected(correctOption.getLetter())
                .build();

        FeedbackAnswer feedback = answerService.answer(answer);

        assertEquals(BigDecimal.valueOf(1.6), feedback.getScore());
    }

    @Test
    @DisplayName("Unsuccess answer by unsuccessful limit")
    void answerWithUnsuccessfulLatencyLimitTest() {
        Answer answer = answerBuilder.millisecondsSpent(50001)
                .optionSelected(correctOption.getLetter())
                .build();

        FeedbackAnswer feedback = answerService.answer(answer);

        assertEquals(BigDecimal.ZERO, feedback.getScore());
    }

    @Test
    @DisplayName("Unsuccess answer by incorrect option selected")
    void unsaccessAnswerTest() {
        Answer answer = answerBuilder.millisecondsSpent(29999)
                .optionSelected(LetterOption.B)
                .build();

        FeedbackAnswer feedback = answerService.answer(answer);

        assertEquals(BigDecimal.ZERO, feedback.getScore());
    }

    @Test
    @DisplayName("Check answer the same questions twice")
    void answerQuestionTwice() {
        Answer answer = answerBuilder.millisecondsSpent(10000)
                .optionSelected(correctOption.getLetter())
                .build();
        when(answerRepository.findByUserIdAndQuestionId(1L, answer.getQuestionId())).thenReturn(answer);

        assertThrows(ValidationException.class, () -> answerService.answer(answer));
    }

    @Test
    @DisplayName("Check feedback includes correct option")
    void feedbackIncludesCorrectOptionTest() {
        Answer answer = answerBuilder.millisecondsSpent(29999)
                .optionSelected(correctOption.getLetter())
                .build();

        FeedbackAnswer feedback = answerService.answer(answer);

        assertEquals(correctOption, feedback.getCorrectOption());
        assertEquals(LetterOption.A, feedback.getCorrectOption().getLetter());
    }

    @Test
    @DisplayName("Check streak is updated when answering")
    void streakIsUpdatedTest() {
        user.setRachaActual(null);
        user.setUltimaActividad(null);

        Answer answer = answerBuilder.millisecondsSpent(29999)
                .optionSelected(correctOption.getLetter())
                .build();

        when(answerRepository.findByUserIdAndQuestionId(user.getId(), answer.getQuestionId())).thenReturn(null);

        answerService.answer(answer);

        assertEquals(1, user.getRachaActual());
        assertEquals(LocalDate.now(), user.getUltimaActividad());
    }
}
