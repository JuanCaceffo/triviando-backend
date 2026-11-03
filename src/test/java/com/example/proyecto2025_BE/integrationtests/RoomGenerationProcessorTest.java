package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.LetterOption;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.llm.Question;
import com.example.proyecto2025_BE.model.dto.llm.QuestionOption;
import com.example.proyecto2025_BE.model.prompter.Prompter;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomGeneration;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.model.room.RoomSource;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomGenerationRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.service.LLMApiClientService;
import com.example.proyecto2025_BE.service.room.RoomGenerationProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:roomtest_generation;DB_CLOSE_DELAY=-1")
@DisplayName("Room Generation Processor Test")
class RoomGenerationProcessorTest {

    @Autowired
    private RoomGenerationProcessor processor;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomMemberRepository memberRepository;
    @Autowired
    private RoomGenerationRepository generationRepository;
    @Autowired
    private RoomQuestionRepository questionRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private LLMApiClientService llmApiClientService;

    private User host;
    private Room room;
    private RoomGeneration generation;

    @BeforeEach
    void setUp() {
        questionRepository.deleteAll();
        generationRepository.deleteAll();
        memberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        host = userRepository.save(User.builder()
                .username("room_host")
                .email("room_host@test.com")
                .password("encoded")
                .name("Room")
                .lastName("Host")
                .account(Account.PREMIUM)
                .build());

        LocalDateTime now = LocalDateTime.now();
        room = roomRepository.save(Room.builder()
                .code("ABC234")
                .hostUserId(host.getId())
                .status(RoomStatus.PREPARING)
                .source(RoomSource.TOPIC)
                .topic("Historia")
                .questionCount(0)
                .currentQuestionIndex(-1)
                .createdAt(now)
                .expiresAt(now.plusMinutes(30))
                .build());

        generation = generationRepository.save(RoomGeneration.builder()
                .roomId(room.getId())
                .status(JobStatus.PENDING)
                .progress(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Test
    @DisplayName("El processor genera preguntas, las guarda en la sala y la pasa a LOBBY")
    void generatesQuestionsAndMovesRoomToLobby() {
        when(llmApiClientService.generateQuestions(any(Prompter.class), eq(host.getId())))
                .thenReturn(List.of(question("¿Capital de Francia?", LetterOption.A),
                        question("¿Capital de Italia?", LetterOption.B)));

        processor.process(generation.getId());

        Room updatedRoom = roomRepository.findById(room.getId()).orElseThrow();
        assertEquals(RoomStatus.LOBBY, updatedRoom.getStatus());
        assertEquals(2, updatedRoom.getQuestionCount());

        List<RoomQuestion> questions = questionRepository.findByRoomIdOrderByPositionAsc(room.getId());
        assertEquals(2, questions.size());
        assertEquals(4, questions.get(0).getOptions().size());
        assertEquals(1, questions.get(0).getOptions().stream().filter(option -> option.isCorrect()).count());

        RoomGeneration updatedGeneration = generationRepository.findById(generation.getId()).orElseThrow();
        assertEquals(JobStatus.COMPLETED, updatedGeneration.getStatus());
        assertEquals(100, updatedGeneration.getProgress());
        assertNull(updatedGeneration.getExtractedText());
        assertTrue(updatedGeneration.getError() == null);
    }

    private Question question(String text, LetterOption correctLetter) {
        QuestionOption correct = new QuestionOption("Correcta", correctLetter);
        List<QuestionOption> options = List.of(
                correct,
                new QuestionOption("Incorrecta 1", LetterOption.A == correctLetter ? LetterOption.B : LetterOption.A),
                new QuestionOption("Incorrecta 2", LetterOption.C),
                new QuestionOption("Incorrecta 3", LetterOption.D));
        return new Question(text, options, correct, Difficulty.LOW, "Explicación");
    }
}
