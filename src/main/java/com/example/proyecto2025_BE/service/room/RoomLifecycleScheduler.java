package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomCloseReason;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Avanza rondas vencidas y cierra salas expiradas. El guard permite desactivar
 * la ejecución automática en tests y llamar a {@link #tick()} manualmente.
 */
@Component
@RequiredArgsConstructor
public class RoomLifecycleScheduler {

    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final RoomGameService gameService;
    private final RoomConnectionRegistry connectionRegistry;
    private final RoomEventPublisher eventPublisher;
    private final RoomProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${api.rooms.tick-ms:500}")
    public void scheduledTick() {
        if (properties.isSchedulerEnabled()) {
            tick();
        }
    }

    public int tick() {
        LocalDateTime now = LocalDateTime.now(clock);
        int changes = 0;

        List<Room> expired = roomRepository.findByStatusInAndExpiresAtBefore(
                RoomService.ACTIVE_STATUSES, now);
        for (Room room : expired) {
            roomService.expireRoom(room.getId(), now);
            eventPublisher.publishClosed(room.getId(), RoomCloseReason.EXPIRED);
            changes++;
        }

        for (Room room : roomRepository.findByStatusIn(List.of(RoomStatus.IN_PROGRESS))) {
            if (gameService.tickRoom(room.getId())) {
                eventPublisher.publishSnapshot(room.getId());
                changes++;
            }
        }

        Instant nowInstant = clock.instant();
        for (Room room : roomRepository.findByStatusIn(RoomService.ACTIVE_STATUSES)) {
            if (connectionRegistry.isConnected(room.getId(), room.getHostUserId())) {
                connectionRegistry.clearHostAbsent(room.getId());
                continue;
            }
            Instant absentSince = connectionRegistry.markHostAbsentIfNeeded(room.getId(), nowInstant);
            if (!absentSince.plusSeconds(properties.getHostGraceSeconds()).isAfter(nowInstant)) {
                roomService.closeForHostAbsence(room.getId(), now);
                eventPublisher.publishClosed(room.getId(), RoomCloseReason.HOST_LEFT);
                changes++;
            }
        }
        return changes;
    }
}
