package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ForbiddenException;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomCloseReason;
import com.example.proyecto2025_BE.model.room.RoomMember;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.views.rooms.RoomReactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out SSE por sala. Mantiene las conexiones en memoria (una sola instancia)
 * y construye un snapshot distinto por jugador, porque cada uno tiene su propio
 * estado "me".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomEventBroker implements RoomEventPublisher {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoomSnapshotMapper snapshotMapper;
    private final RoomConnectionRegistry connectionRegistry;
    private final RoomProperties properties;
    private final Clock clock;

    private final Map<Long, List<ClientConnection>> connections = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String code, Long userId) {
        return subscribe(code, userId, new SseEmitter(properties.getSseTimeoutMs()));
    }

    SseEmitter subscribe(String code, Long userId, SseEmitter emitter) {
        Room room = roomRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> NotFoundException.build(Exceptions.ROOM_NOT_FOUND));
        assertMember(room.getId(), userId);

        ClientConnection connection = new ClientConnection(userId, emitter);
        synchronized (lock(room.getId())) {
            connections.computeIfAbsent(room.getId(), key -> new CopyOnWriteArrayList<>())
                    .add(connection);
            connectionRegistry.register(room.getId(), userId);
            sendSnapshot(room, connection);
            if (terminal(room)) {
                send(room.getId(), connection, "closed",
                        Map.of("reason", room.getCloseReason() == null ? "CLOSED" : room.getCloseReason().name()));
                emitter.complete();
                remove(room.getId(), connection);
            }
        }

        if (!terminal(room)) {
            // El resto de la sala necesita enterarse de que este jugador se conectó.
            publishSnapshotToOthers(room.getId(), connection);
        }

        emitter.onCompletion(() -> {
            remove(room.getId(), connection);
            publishSnapshot(room.getId());
        });
        emitter.onTimeout(() -> {
            remove(room.getId(), connection);
            publishSnapshot(room.getId());
        });
        emitter.onError(error -> {
            remove(room.getId(), connection);
            publishSnapshot(room.getId());
        });
        return emitter;
    }

    @Override
    public void publishSnapshot(Long roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            publishClosed(roomId, RoomCloseReason.EMPTY);
            return;
        }
        synchronized (lock(roomId)) {
            List<ClientConnection> roomConnections = connections.get(roomId);
            if (roomConnections == null) {
                return;
            }
            for (ClientConnection connection : roomConnections) {
                sendSnapshot(room, connection);
            }
        }
    }

    @Override
    public void publishReaction(Long roomId, Long userId, String emoji) {
        User user = userRepository.findById(userId).orElse(null);
        RoomReactionEvent event = new RoomReactionEvent(
                userId,
                user == null ? "" : user.getUsername(),
                emoji,
                clock.millis());
        broadcast(roomId, "reaction", event);
    }

    @Override
    public void publishClosed(Long roomId, RoomCloseReason reason) {
        synchronized (lock(roomId)) {
            List<ClientConnection> roomConnections = connections.remove(roomId);
            if (roomConnections != null) {
                for (ClientConnection connection : roomConnections) {
                    send(roomId, connection, "closed", Map.of("reason", reason.name()));
                    try {
                        connection.emitter().complete();
                    } catch (Exception exception) {
                        log.debug("No se pudo completar el emitter de la sala {}", roomId);
                    }
                }
            }
            connectionRegistry.clearRoom(roomId);
        }
    }

    /**
     * Cierra únicamente las conexiones de un jugador que abandonó la sala.
     */
    public void disconnectUser(Long roomId, Long userId) {
        synchronized (lock(roomId)) {
            List<ClientConnection> roomConnections = connections.get(roomId);
            if (roomConnections == null) {
                return;
            }
            List<ClientConnection> target = roomConnections.stream()
                    .filter(connection -> connection.userId().equals(userId))
                    .toList();
            roomConnections.removeAll(target);
            for (ClientConnection connection : target) {
                // Cierre limpio: el cliente sabe que el corte es intencional y
                // no lo interpreta como un error de red.
                send(roomId, connection, "closed", Map.of("reason", "LEFT"));
                try {
                    connection.emitter().complete();
                } catch (Exception exception) {
                    log.debug("No se pudo completar el emitter del jugador {}", userId);
                }
                connectionRegistry.unregister(roomId, userId);
            }
            if (roomConnections.isEmpty()) {
                connections.remove(roomId);
            }
        }
    }

    @Scheduled(fixedDelayString = "${api.rooms.heartbeat-ms:15000}")
    public void scheduledHeartbeat() {
        if (properties.isSchedulerEnabled()) {
            heartbeat();
        }
    }

    public void heartbeat() {
        List<Long> changedRooms = new ArrayList<>();
        for (Long roomId : new ArrayList<>(connections.keySet())) {
            synchronized (lock(roomId)) {
                List<ClientConnection> roomConnections = connections.get(roomId);
                if (roomConnections == null) {
                    continue;
                }
                List<ClientConnection> dead = new ArrayList<>();
                for (ClientConnection connection : roomConnections) {
                    try {
                        connection.emitter().send(SseEmitter.event().comment("ping"));
                    } catch (Exception exception) {
                        dead.add(connection);
                    }
                }
                roomConnections.removeAll(dead);
                dead.forEach(connection -> connectionRegistry.unregister(roomId, connection.userId()));
                if (roomConnections.isEmpty()) {
                    connections.remove(roomId);
                }
                if (!dead.isEmpty()) {
                    changedRooms.add(roomId);
                }
            }
        }
        changedRooms.forEach(this::publishSnapshot);
    }

    /**
     * Manda el snapshot actualizado a todos menos a la conexión que acaba de
     * entrar, para que el resto vea su cambio de presencia sin duplicarle el
     * snapshot inicial al jugador nuevo.
     */
    private void publishSnapshotToOthers(Long roomId, ClientConnection excluded) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            publishClosed(roomId, RoomCloseReason.EMPTY);
            return;
        }
        synchronized (lock(roomId)) {
            List<ClientConnection> roomConnections = connections.get(roomId);
            if (roomConnections == null) {
                return;
            }
            for (ClientConnection connection : roomConnections) {
                if (connection != excluded) {
                    sendSnapshot(room, connection);
                }
            }
        }
    }

    private void broadcast(Long roomId, String event, Object data) {
        synchronized (lock(roomId)) {
            List<ClientConnection> roomConnections = connections.get(roomId);
            if (roomConnections == null) {
                return;
            }
            for (ClientConnection connection : roomConnections) {
                send(roomId, connection, event, data);
            }
        }
    }

    private void sendSnapshot(Room room, ClientConnection connection) {
        try {
            connection.emitter().send(SseEmitter.event()
                    .name("snapshot")
                    .data(snapshotMapper.toSnapshot(room, connection.userId())));
        } catch (Exception exception) {
            log.debug("Se removió una conexión SSE de la sala {} por error de envío", room.getId());
            remove(room.getId(), connection);
        }
    }

    private void send(Long roomId, ClientConnection connection, String event, Object data) {
        try {
            connection.emitter().send(SseEmitter.event().name(event).data(data));
        } catch (Exception exception) {
            remove(roomId, connection);
        }
    }

    private void remove(Long roomId, ClientConnection connection) {
        synchronized (lock(roomId)) {
            List<ClientConnection> roomConnections = connections.get(roomId);
            if (roomConnections != null && roomConnections.remove(connection)) {
                connectionRegistry.unregister(roomId, connection.userId());
                if (roomConnections.isEmpty()) {
                    connections.remove(roomId);
                }
            }
        }
    }

    private void assertMember(Long roomId, Long userId) {
        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> ForbiddenException.build(Exceptions.ROOM_NOT_MEMBER));
        if (member.getLeftAt() != null) {
            throw ForbiddenException.build(Exceptions.ROOM_NOT_MEMBER);
        }
    }

    private boolean terminal(Room room) {
        return RoomStatus.CLOSED.equals(room.getStatus())
                || RoomStatus.FAILED.equals(room.getStatus());
    }

    private Object lock(Long roomId) {
        return locks.computeIfAbsent(roomId, key -> new Object());
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private record ClientConnection(Long userId, SseEmitter emitter) {
    }
}
