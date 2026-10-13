package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.repository.ResponseRepository;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.*;
import com.example.proyecto2025_BE.service.AnswerService;
import com.example.proyecto2025_BE.service.IQuestion;
import com.example.proyecto2025_BE.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Answer Service Test")
public class AnswerServiceTest {

	private static AnswerService answerService;
    private static Option correctOption;
	private static Answer.AnswerBuilder answerbuilder;
	private static ResponseRepository answerRepository;
	private static User user;

    @BeforeAll
	static void beforeAll() {
        UserService userServiceMock = mock(UserService.class);
        IQuestion IQuestionMock = mock(IQuestion.class);
		answerRepository = mock(ResponseRepository.class);
		answerService = new AnswerService(userServiceMock, IQuestionMock, answerRepository);
		
		user = User.builder()
				.id(1L)
				.build();
		
		correctOption = Option.builder()
				.letter(LetterOption.A)
				.build();
		
		Pregunta pregunta = Pregunta.builder()
				.correctOption(correctOption)
				.difficulty(Difficulty.MEDIUM)
				.build();
		
		answerbuilder = Answer.builder()
				.questionId("e3r4g5th4nb3rg4t")
				.user(user);
		
		when(userServiceMock.retrieve(anyLong())).thenReturn(user);
		when(IQuestionMock.getPreguntaById(anyString())).thenReturn(pregunta);
	}
	
	@BeforeEach
	void beforeEach() {
		
	}
	
	@Test
	@DisplayName("Success answer without latency penalty limit, without unsuccessful limit")
	void answerTest() {
		Answer answer = answerbuilder.millisecondsSpent(29999)
				.optionSelected(correctOption.getLetter())
				.build();
		
		FeedbackAnswer feedback = answerService.answer(answer);
		
		assertEquals(BigDecimal.valueOf(16), feedback.getScore());
	}
	
	@Test
	@DisplayName("Success answer with latency penalty limit, without unsuccessful limit")
	void answerWithLatencyPenaltyLimitTest() {
		Answer answer = answerbuilder.millisecondsSpent(30001)
				.optionSelected(correctOption.getLetter())
				.build();
		
		FeedbackAnswer feedback = answerService.answer(answer);
		
		assertEquals(BigDecimal.valueOf(1.6), feedback.getScore());
	}
	
	@Test
	@DisplayName("Unsuccess answer by unsuccessful limit")
	void answerWithUnsuccessfulLatencyLimitTest() {
		Answer answer = answerbuilder.millisecondsSpent(50001)
				.optionSelected(correctOption.getLetter())
				.build();
		
		FeedbackAnswer feedback = answerService.answer(answer);
		
		assertEquals(BigDecimal.ZERO, feedback.getScore());
	}
	
	@Test
	@DisplayName("Unsuccess answer by incorrect option selected")
	void unsaccessAnswerTest() {
		Option incorrectOption = Option.builder()
				.letter(LetterOption.B)
				.build();
		
		Answer answer = answerbuilder.millisecondsSpent(29999)
				.optionSelected(incorrectOption.getLetter())
				.build();
		
		FeedbackAnswer feedback = answerService.answer(answer);
		
		assertEquals(BigDecimal.ZERO, feedback.getScore());
	}

	@Test
	@DisplayName("Check answer the same questions twice")
	void answerQuestionTwice() {
		Answer answer = answerbuilder.millisecondsSpent(10000)
				.optionSelected(correctOption.getLetter())
				.build();
		when(answerRepository.findByUserIdAndQuestionId(1L, answer.getQuestionId())).thenReturn(answer);

		assertThrows(ValidationException.class, () -> answerService.answer(answer));
	}

	@Test
	@DisplayName("Check feedback includes correct option")
	void feedbackIncludesCorrectOptionTest() {
		Answer answer = answerbuilder.millisecondsSpent(29999)
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

		Answer answer = answerbuilder.millisecondsSpent(29999)
				.optionSelected(correctOption.getLetter())
				.build();

		when(answerRepository.findByUserIdAndQuestionId(user.getId(), answer.getQuestionId())).thenReturn(null);

		answerService.answer(answer);

		assertEquals(1, user.getRachaActual());
		assertEquals(LocalDate.now(), user.getUltimaActividad());
	}
}