package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.LetterOption;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomAnswer;
import com.example.proyecto2025_BE.model.room.RoomOption;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomAnswerRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.service.room.RoomGameService;
import com.example.proyecto2025_BE.service.room.RoomGenerationWorker;
import com.example.proyecto2025_BE.service.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:roomtest_game;DB_CLOSE_DELAY=-1")
@DisplayName("Room Game Service Test")
class RoomGameServiceTest {

    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomGameService gameService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomMemberRepository memberRepository;
    @Autowired
    private RoomQuestionRepository questionRepository;
    @Autowired
    private RoomAnswerRepository answerRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RoomGenerationWorker worker;

    private User host;
    private User guest;
    private Room room;
    private RoomQuestion question;

    @BeforeEach
    void setUp() {
        answerRepository.deleteAll();
        questionRepository.deleteAll();
        memberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        host = saveUser("room_host", Account.PREMIUM);
        guest = saveUser("room_guest", Account.FREE);

        room = roomService.createTopicRoom("Historia", host.getId(), Account.PREMIUM);
        room.setStatus(RoomStatus.LOBBY);
        room.setQuestionCount(1);
        roomRepository.save(room);
        roomService.joinRoom(room.getCode(), guest.getId());
        room = roomService.startRoom(room.getCode(), host.getId());

        question = saveQuestion(room.getId(), 0);
    }

    @Test
    @DisplayName("Un miembro responde la pregunta actual y el servidor calcula acierto y puntos")
    void memberAnswersCurrentQuestion() {
        LocalDateTime now = LocalDateTime.now();
        room.setQuestionStartedAt(now.minusSeconds(10));
        room.setQuestionDeadlineAt(now.plusSeconds(20));
        roomRepository.save(room);

        RoomAnswer answer = gameService.answer(room.getCode(), guest.getId(), question.getId(), LetterOption.A);

        assertTrue(answer.isCorrect());
        assertTrue(answer.getPoints() >= 100 && answer.getPoints() <= 200);
        assertTrue(answer.getMillisecondsSpent() >= 9_000 && answer.getMillisecondsSpent() <= 12_000);
        assertEquals(room.getId(), answer.getRoomId());
        assertEquals(guest.getId(), answer.getUserId());
        assertTrue(answerRepository.findByQuestionIdAndUserId(question.getId(), guest.getId()).isPresent());
    }

    @Test
    @DisplayName("La ronda cierra cuando todos respondieron y finaliza después del reveal")
    void closesRoundWhenAllMembersAnsweredAndFinishesAfterReveal() {
        LocalDateTime now = LocalDateTime.now();
        room.setQuestionStartedAt(now.minusSeconds(5));
        room.setQuestionDeadlineAt(now.plusSeconds(25));
        roomRepository.save(room);

        RoomAnswer guestAnswer = gameService.answer(room.getCode(), guest.getId(), question.getId(), LetterOption.A);
        gameService.answer(room.getCode(), host.getId(), question.getId(), LetterOption.B);

        assertTrue(gameService.tickRoom(room.getId()));

        Room closed = roomRepository.findById(room.getId()).orElseThrow();
        assertNotNull(closed.getRevealUntil());
        assertEquals(guestAnswer.getPoints(),
                memberRepository.findByRoomIdAndUserId(room.getId(), guest.getId()).orElseThrow().getScore());
        assertEquals(0,
                memberRepository.findByRoomIdAndUserId(room.getId(), host.getId()).orElseThrow().getScore());
        assertEquals(1,
                memberRepository.findByRoomIdAndUserId(room.getId(), guest.getId()).orElseThrow().getCorrectCount());

        closed.setRevealUntil(LocalDateTime.now().minusSeconds(1));
        roomRepository.save(closed);

        assertTrue(gameService.tickRoom(room.getId()));

        Room finished = roomRepository.findById(room.getId()).orElseThrow();
        assertEquals(RoomStatus.FINISHED, finished.getStatus());
        assertNotNull(finished.getFinishedAt());
    }

    private RoomQuestion saveQuestion(Long roomId, int position) {
        RoomQuestion question = RoomQuestion.builder()
                .roomId(roomId)
                .position(position)
                .enunciado("¿Cuál es la capital de Francia?")
                .explicacion("París es la capital de Francia.")
                .difficulty(Difficulty.LOW)
                .createdAt(LocalDateTime.now())
                .build();

        List<RoomOption> options = new ArrayList<>();
        options.add(option(question, LetterOption.A, "París", true, 0));
        options.add(option(question, LetterOption.B, "Londres", false, 1));
        options.add(option(question, LetterOption.C, "Roma", false, 2));
        options.add(option(question, LetterOption.D, "Madrid", false, 3));
        question.setOptions(options);
        return questionRepository.save(question);
    }

    private RoomOption option(RoomQuestion question, LetterOption letter, String text, boolean correct, int order) {
        return RoomOption.builder()
                .question(question)
                .letter(letter)
                .text(text)
                .correct(correct)
                .positionOrder(order)
                .build();
    }

    private User saveUser(String username, Account account) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .password("encoded")
                .name(username)
                .lastName("Test")
                .account(account)
                .build());
    }
}
