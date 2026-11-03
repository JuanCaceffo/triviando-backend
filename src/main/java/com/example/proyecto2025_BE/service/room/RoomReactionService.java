package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reacciones emoji efímeras: whitelist y cooldown por usuario. No se guardan.
 */
@Service
@RequiredArgsConstructor
public class RoomReactionService {

    static final Set<String> ALLOWED = Set.of("👍", "😂", "🔥", "😮", "🎉");

    private final RoomService roomService;
    private final RoomEventPublisher publisher;
    private final RoomProperties properties;
    private final Clock clock;

    private final Map<Long, Instant> lastReaction = new ConcurrentHashMap<>();

    public void react(String code, Long userId, String emoji) {
        if (emoji == null || !ALLOWED.contains(emoji)) {
            throw ValidationException.build(Exceptions.ROOM_REACTION_INVALID);
        }

        Room room = roomService.getByCode(code);
        roomService.requireMember(code, userId);
        if (!RoomStatus.PREPARING.equals(room.getStatus())
                && !RoomStatus.LOBBY.equals(room.getStatus())
                && !RoomStatus.IN_PROGRESS.equals(room.getStatus())) {
            throw ConflictException.build(Exceptions.ROOM_REACTION_NOT_ALLOWED);
        }

        Instant now = clock.instant();
        Instant previous = lastReaction.get(userId);
        if (previous != null
                && previous.plusMillis(properties.getReactionCooldownMs()).isAfter(now)) {
            throw ConflictException.build(Exceptions.ROOM_REACTION_TOO_FAST);
        }
        lastReaction.put(userId, now);
        publisher.publishReaction(room.getId(), userId, emoji);
    }
}
