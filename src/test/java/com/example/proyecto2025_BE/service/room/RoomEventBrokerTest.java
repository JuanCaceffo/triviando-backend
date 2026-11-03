package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomCloseReason;
import com.example.proyecto2025_BE.repository.RoomAnswerRepository;
import com.example.proyecto2025_BE.repository.RoomGenerationRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.views.rooms.RoomPlayerResponse;
import com.example.proyecto2025_BE.views.rooms.RoomReactionEvent;
import com.example.proyecto2025_BE.views.rooms.RoomSnapshotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:roomtest_broker;DB_CLOSE_DELAY=-1")
@DisplayName("Room Event Broker Test")
class RoomEventBrokerTest {

    @Autowired
    private RoomEventBroker broker;
    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomConnectionRegistry connectionRegistry;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomMemberRepository memberRepository;
    @Autowired
    private RoomQuestionRepository questionRepository;
    @Autowired
    private RoomAnswerRepository answerRepository;
    @Autowired
    private RoomGenerationRepository generationRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RoomGenerationWorker worker;

    private User host;
    private User guest;
    private Room room;
    private SseEmitter emitter;

    @BeforeEach
    void setUp() {
        answerRepository.deleteAll();
        questionRepository.deleteAll();
        generationRepository.deleteAll();
        memberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        host = saveUser("room_host", Account.PREMIUM);
        guest = saveUser("room_guest", Account.FREE);
        room = roomService.createTopicRoom("Historia", host.getId(), Account.PREMIUM);
        roomService.joinRoom(room.getCode(), guest.getId());

        emitter = mock(SseEmitter.class);
        clearInvocations(emitter);
    }

    @Test
    @DisplayName("Al suscribirse recibe un snapshot propio y el broker publica reacciones y cierre")
    void subscribesReceivesSnapshotAndReactions() throws Exception {
        broker.subscribe(room.getCode(), guest.getId(), emitter);

        assertTrue(connectionRegistry.isConnected(room.getId(), guest.getId()));
        RoomSnapshotResponse initial = lastSnapshot(emitter);
        assertEquals(room.getCode(), initial.code());
        assertEquals(guest.getId(), initial.me().userId());
        assertFalse(initial.players().isEmpty());

        clearInvocations(emitter);
        broker.publishReaction(room.getId(), guest.getId(), "🔥");

        RoomReactionEvent reaction = lastData(emitter, RoomReactionEvent.class);
        assertEquals("🔥", reaction.emoji());
        assertEquals("room_guest", reaction.username());

        broker.publishClosed(room.getId(), RoomCloseReason.HOST_CLOSED);
        verify(emitter).complete();
        assertFalse(connectionRegistry.isConnected(room.getId(), guest.getId()));
    }

    @Test
    @DisplayName("Dos pestañas del mismo usuario no se desconectan hasta cerrar la última")
    void multipleTabsKeepPresence() {
        SseEmitter second = mock(SseEmitter.class);
        broker.subscribe(room.getCode(), guest.getId(), emitter);
        broker.subscribe(room.getCode(), guest.getId(), second);

        connectionRegistry.unregister(room.getId(), guest.getId());
        assertTrue(connectionRegistry.isConnected(room.getId(), guest.getId()));
        connectionRegistry.unregister(room.getId(), guest.getId());
        assertFalse(connectionRegistry.isConnected(room.getId(), guest.getId()));
    }

    @Test
    @DisplayName("Cuando se conecta un jugador, el resto recibe su presencia actualizada")
    void subscribingNotifiesOtherPlayers() {
        SseEmitter hostEmitter = mock(SseEmitter.class);
        broker.subscribe(room.getCode(), host.getId(), hostEmitter);
        clearInvocations(hostEmitter);

        SseEmitter guestEmitter = mock(SseEmitter.class);
        broker.subscribe(room.getCode(), guest.getId(), guestEmitter);

        RoomSnapshotResponse snapshot = lastSnapshot(hostEmitter);
        RoomPlayerResponse guestPlayer = snapshot.players().stream()
                .filter(player -> player.userId().equals(guest.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(guestPlayer.connected());
    }

    @Test
    @DisplayName("Al salir, el jugador recibe un closed LEFT antes de completar su emitter")
    void disconnectUserSendsClosedEvent() {
        broker.subscribe(room.getCode(), guest.getId(), emitter);
        clearInvocations(emitter);

        broker.disconnectUser(room.getId(), guest.getId());

        assertEquals("LEFT", lastMap(emitter).get("reason"));
        verify(emitter).complete();
        assertFalse(connectionRegistry.isConnected(room.getId(), guest.getId()));
    }

    @Test
    @DisplayName("Cuando se desconecta un jugador, el resto recibe su presencia actualizada")
    void disconnectingNotifiesOtherPlayers() {
        SseEmitter hostEmitter = mock(SseEmitter.class);
        broker.subscribe(room.getCode(), host.getId(), hostEmitter);
        SseEmitter guestEmitter = mock(SseEmitter.class);
        broker.subscribe(room.getCode(), guest.getId(), guestEmitter);

        ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
        verify(guestEmitter).onCompletion(onCompletion.capture());
        clearInvocations(hostEmitter);

        onCompletion.getValue().run();

        RoomSnapshotResponse snapshot = lastSnapshot(hostEmitter);
        RoomPlayerResponse guestPlayer = snapshot.players().stream()
                .filter(player -> player.userId().equals(guest.getId()))
                .findFirst()
                .orElseThrow();
        assertFalse(guestPlayer.connected());
    }

    private RoomSnapshotResponse lastSnapshot(SseEmitter target) {
        return lastData(target, RoomSnapshotResponse.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> lastMap(SseEmitter target) {
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        try {
            verify(target, atLeastOnce()).send(captor.capture());
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        for (int i = captor.getAllValues().size() - 1; i >= 0; i--) {
            for (ResponseBodyEmitter.DataWithMediaType item : captor.getAllValues().get(i).build()) {
                if (item.getData() instanceof Map<?, ?> map) {
                    return (Map<String, String>) map;
                }
            }
        }
        throw new AssertionError("No se encontró un evento Map");
    }

    private <T> T lastData(SseEmitter target, Class<T> type) {
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        try {
            verify(target, atLeastOnce()).send(captor.capture());
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        for (int i = captor.getAllValues().size() - 1; i >= 0; i--) {
            for (ResponseBodyEmitter.DataWithMediaType item : captor.getAllValues().get(i).build()) {
                if (type.isInstance(item.getData())) {
                    return type.cast(item.getData());
                }
            }
        }
        throw new AssertionError("No se encontró un evento de tipo " + type.getSimpleName());
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
