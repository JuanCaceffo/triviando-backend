package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomGeneration;
import com.example.proyecto2025_BE.model.room.RoomMember;
import com.example.proyecto2025_BE.model.room.RoomSource;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomGenerationRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.service.room.RoomGenerationWorker;
import com.example.proyecto2025_BE.service.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:roomtest_service;DB_CLOSE_DELAY=-1")
@DisplayName("Room Service Test")
class RoomServiceTest {

    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomMemberRepository memberRepository;
    @Autowired
    private RoomGenerationRepository generationRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RoomGenerationWorker worker;

    private User host;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    @DisplayName("Un anfitrión PREMIUM crea una sala por tema en PREPARING, con código y como miembro")
    void premiumHostCreatesTopicRoom() {
        Room room = roomService.createTopicRoom("Historia", host.getId(), Account.PREMIUM);

        assertNotNull(room.getId());
        assertEquals(RoomStatus.PREPARING, room.getStatus());
        assertEquals(RoomSource.TOPIC, room.getSource());
        assertEquals("Historia", room.getTopic());
        assertEquals(host.getId(), room.getHostUserId());
        assertEquals(0, room.getQuestionCount());
        assertEquals(-1, room.getCurrentQuestionIndex());
        assertTrue(room.getCode().matches("^[A-HJ-NP-Z2-9]{6}$"));
        assertTrue(room.getExpiresAt().isAfter(LocalDateTime.now().minusSeconds(1)));

        assertTrue(roomRepository.findByCode(room.getCode()).isPresent());
        assertTrue(memberRepository.findByRoomIdAndUserId(room.getId(), host.getId()).isPresent());

        RoomGeneration generation = generationRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals(JobStatus.PENDING, generation.getStatus());
        assertEquals(0, generation.getProgress());
        verify(worker).process(generation.getId());
    }

    @Test
    @DisplayName("Un usuario FREE puede unirse a una sala PREPARING y el join es idempotente")
    void freeUserCanJoinPreparingRoomIdempotently() {
        Room room = roomService.createTopicRoom("Historia", host.getId(), Account.PREMIUM);
        User guest = saveUser("room_guest", Account.FREE);

        RoomMember first = roomService.joinRoom(room.getCode(), guest.getId());
        RoomMember second = roomService.joinRoom(room.getCode(), guest.getId());

        assertEquals(first.getId(), second.getId());
        assertEquals(2, memberRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).size());
    }

    @Test
    @DisplayName("El anfitrión inicia una sala LOBBY con dos jugadores y arranca la primera pregunta")
    void hostStartsLobbyRoomWithTwoPlayers() {
        Room room = roomService.createTopicRoom("Historia", host.getId(), Account.PREMIUM);
        room.setStatus(RoomStatus.LOBBY);
        room.setQuestionCount(10);
        roomRepository.save(room);

        User guest = saveUser("room_guest", Account.FREE);
        roomService.joinRoom(room.getCode(), guest.getId());

        Room started = roomService.startRoom(room.getCode(), host.getId());

        assertEquals(RoomStatus.IN_PROGRESS, started.getStatus());
        assertEquals(0, started.getCurrentQuestionIndex());
        assertNotNull(started.getQuestionStartedAt());
        assertNotNull(started.getQuestionDeadlineAt());
        assertTrue(started.getQuestionDeadlineAt().isAfter(started.getQuestionStartedAt()));
        assertTrue(started.getQuestionDeadlineAt().isBefore(started.getQuestionStartedAt().plusSeconds(31)));
    }

    @Test
    @DisplayName("Un anfitrión PREMIUM crea una sala desde archivo y conserva el texto para el worker")
    void premiumHostCreatesFileRoom() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "apuntes.txt", "text/plain",
                "La célula es la unidad básica de la vida y contiene organelas.".getBytes());

        Room room = roomService.createFileRoom("Biología", file, host.getId(), Account.PREMIUM);

        assertEquals(RoomSource.FILE, room.getSource());
        RoomGeneration generation = generationRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals(JobStatus.PENDING, generation.getStatus());
        assertNotNull(generation.getExtractedText());
        assertTrue(generation.getExtractedText().contains("célula"));
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
