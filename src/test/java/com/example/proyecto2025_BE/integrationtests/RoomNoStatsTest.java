package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.LetterOption;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.StatsResponse;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomOption;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.QuestionRepository;
import com.example.proyecto2025_BE.repository.ResponseRepository;
import com.example.proyecto2025_BE.repository.RoomAnswerRepository;
import com.example.proyecto2025_BE.repository.RoomGenerationRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.repository.TopicRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.service.UserService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:roomtest_nostats;DB_CLOSE_DELAY=-1")
@DisplayName("Room No Stats Test")
class RoomNoStatsTest {

    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomGameService gameService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomMemberRepository memberRepository;
    @Autowired
    private RoomQuestionRepository roomQuestionRepository;
    @Autowired
    private RoomAnswerRepository roomAnswerRepository;
    @Autowired
    private RoomGenerationRepository generationRepository;
    @Autowired
    private ResponseRepository responseRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private TopicRepository topicRepository;

    @MockitoBean
    private RoomGenerationWorker worker;

    private User host;
    private User guest;

    @BeforeEach
    void setUp() {
        roomAnswerRepository.deleteAll();
        roomQuestionRepository.deleteAll();
        generationRepository.deleteAll();
        memberRepository.deleteAll();
        roomRepository.deleteAll();
        responseRepository.deleteAll();
        questionRepository.deleteAll();
        topicRepository.deleteAll();
        userRepository.deleteAll();

        host = userRepository.save(User.builder()
                .username("room_host")
                .email("room_host@test.com")
                .password("encoded")
                .name("Room")
                .lastName("Host")
                .account(Account.PREMIUM)
                .joinDate(LocalDateTime.now().minusMonths(1))
                .rachaActual(3)
                .ultimaActividad(LocalDate.now().minusDays(1))
                .build());
        guest = userRepository.save(User.builder()
                .username("room_guest")
                .email("room_guest@test.com")
                .password("encoded")
                .name("Room")
                .lastName("Guest")
                .account(Account.FREE)
                .joinDate(LocalDateTime.now().minusMonths(1))
                .build());
    }

    @Test
    @DisplayName("Una partida completa no modifica answer, score, racha, ranking, topics ni questions")
    void playingRoomDoesNotTouchGlobalStats() {
        StatsResponse statsBefore = userService.getStatisticsFromUser(host.getId());
        BigDecimal scoreBefore = userService.retrieveWithScore(host.getId()).getScore();
        int rachaBefore = host.getRachaActual();
        LocalDate lastActivityBefore = host.getUltimaActividad();
        long answersBefore = responseRepository.count();
        long topicsBefore = topicRepository.count();
        long questionsBefore = questionRepository.count();

        Room room = roomService.createTopicRoom("Historia", host.getId(), Account.PREMIUM);
        room.setStatus(RoomStatus.LOBBY);
        room.setQuestionCount(1);
        roomRepository.save(room);
        roomService.joinRoom(room.getCode(), guest.getId());
        room = roomService.startRoom(room.getCode(), host.getId());

        RoomQuestion question = saveQuestion(room.getId());
        LocalDateTime now = LocalDateTime.now();
        room.setQuestionStartedAt(now.minusSeconds(5));
        room.setQuestionDeadlineAt(now.plusSeconds(25));
        roomRepository.save(room);

        gameService.answer(room.getCode(), host.getId(), question.getId(), LetterOption.A);
        gameService.answer(room.getCode(), guest.getId(), question.getId(), LetterOption.A);
        gameService.tickRoom(room.getId());

        Room reveal = roomRepository.findById(room.getId()).orElseThrow();
        reveal.setRevealUntil(LocalDateTime.now().minusSeconds(1));
        roomRepository.save(reveal);
        gameService.tickRoom(room.getId());

        User hostAfter = userRepository.findById(host.getId()).orElseThrow();
        assertEquals(rachaBefore, hostAfter.getRachaActual());
        assertEquals(lastActivityBefore, hostAfter.getUltimaActividad());
        assertEquals(statsBefore, userService.getStatisticsFromUser(host.getId()));
        assertEquals(0, scoreBefore.compareTo(userService.retrieveWithScore(host.getId()).getScore()));
        assertEquals(answersBefore, responseRepository.count());
        assertEquals(topicsBefore, topicRepository.count());
        assertEquals(questionsBefore, questionRepository.count());
        assertEquals(0, responseRepository.count());
        assertEquals(0, topicRepository.count());
        assertEquals(0, questionRepository.count());
    }

    private RoomQuestion saveQuestion(Long roomId) {
        RoomQuestion question = RoomQuestion.builder()
                .roomId(roomId)
                .position(0)
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
        return roomQuestionRepository.save(question);
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
}
