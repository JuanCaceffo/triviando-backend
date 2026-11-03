package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.LetterOption;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomOption;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomAnswerRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.security.JwtUtil;
import com.example.proyecto2025_BE.service.room.RoomGenerationWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:roomtest_controller;DB_CLOSE_DELAY=-1")
@DisplayName("Room Controller Test")
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomMemberRepository memberRepository;
    @Autowired
    private RoomQuestionRepository questionRepository;
    @Autowired
    private RoomAnswerRepository answerRepository;

    @MockitoBean
    private RoomGenerationWorker worker;

    private User host;
    private User guest;
    private String hostToken;
    private String guestToken;

    @BeforeEach
    void setUp() {
        answerRepository.deleteAll();
        questionRepository.deleteAll();
        memberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        host = saveUser("room_host", Account.PREMIUM);
        guest = saveUser("room_guest", Account.FREE);
        hostToken = token(host);
        guestToken = token(guest);
    }

    @Test
    @DisplayName("Un PREMIUM crea la sala y un FREE se une con el código")
    void premiumCreatesRoomAndFreeUserJoins() throws Exception {
        String code = createRoom();

        mockMvc.perform(post("/rooms/{code}/join", code)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.players.length()").value(2))
                .andExpect(jsonPath("$.me.userId").value(guest.getId()));
    }

    @Test
    @DisplayName("Un PREMIUM crea una sala desde archivo")
    void premiumCreatesRoomFromFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "apuntes.txt", "text/plain",
                "La célula es la unidad básica de la vida y contiene organelas.".getBytes());

        mockMvc.perform(multipart("/rooms/document")
                        .file(file)
                        .param("topic", "Biología")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    @Test
    @DisplayName("Un usuario FREE no puede crear una sala desde archivo")
    void freeUserCannotCreateRoomFromFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "apuntes.txt", "text/plain",
                "La célula es la unidad básica de la vida y contiene organelas.".getBytes());

        mockMvc.perform(multipart("/rooms/document")
                        .file(file)
                        .param("topic", "Biología")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un usuario FREE no puede crear una sala")
    void freeUserCannotCreateRoom() throws Exception {
        mockMvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"Historia\"}")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Antes del reveal el snapshot no expone la opción correcta ni la explicación")
    void snapshotDoesNotLeakCorrectAnswerBeforeReveal() throws Exception {
        String code = createRoom();
        prepareLobbyWithQuestion(code);
        mockMvc.perform(post("/rooms/{code}/join", code)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/rooms/{code}/start", code)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentQuestion.correctOptionLetter").doesNotExist())
                .andExpect(jsonPath("$.currentQuestion.explanation").doesNotExist());

        mockMvc.perform(get("/rooms/{code}", code)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentQuestion.correctOptionLetter").doesNotExist())
                .andExpect(jsonPath("$.currentQuestion.explanation").doesNotExist());
    }

    @Test
    @DisplayName("Responder devuelve solo la confirmación y no el acierto ni los puntos")
    void answerReturnsOnlyAcknowledgement() throws Exception {
        String code = createRoom();
        Room room = prepareLobbyWithQuestion(code);
        mockMvc.perform(post("/rooms/{code}/join", code)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/rooms/{code}/start", code)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        RoomQuestion question = questionRepository.findByRoomIdAndPosition(room.getId(), 0).orElseThrow();

        mockMvc.perform(post("/rooms/{code}/answers", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionId", question.getId(),
                                "option", "A")))
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.option").value("A"))
                .andExpect(jsonPath("$.correct").doesNotExist())
                .andExpect(jsonPath("$.points").doesNotExist());

        mockMvc.perform(get("/rooms/{code}", code)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.answeredCurrent").value(true))
                .andExpect(jsonPath("$.me.selectedOption").value("A"))
                .andExpect(jsonPath("$.me.pointsLastRound").doesNotExist())
                .andExpect(jsonPath("$.me.correctLastRound").doesNotExist())
                .andExpect(jsonPath("$.currentQuestion.correctOptionLetter").doesNotExist());
    }

    @Test
    @DisplayName("Un usuario que no es miembro no puede leer la sala")
    void nonMemberCannotReadRoom() throws Exception {
        String code = createRoom();

        mockMvc.perform(get("/rooms/{code}", code)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
    }

    private String createRoom() throws Exception {
        MvcResult result = mockMvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"Historia\"}")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("code").asText();
    }

    private Room prepareLobbyWithQuestion(String code) {
        Room room = roomRepository.findByCode(code).orElseThrow();
        room.setStatus(RoomStatus.LOBBY);
        room.setQuestionCount(1);
        roomRepository.save(room);

        RoomQuestion question = RoomQuestion.builder()
                .roomId(room.getId())
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
        questionRepository.save(question);
        return room;
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

    private String token(User user) {
        return jwtUtil.generateToken(
                Map.of("id", user.getId(), "fullname", user.getFullName(), "account", user.getAccount()),
                user.getUsername());
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
