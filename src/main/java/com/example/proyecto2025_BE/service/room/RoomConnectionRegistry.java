package com.example.proyecto2025_BE.service.room;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presencia efímera de las conexiones SSE. No se persiste: tras un reinicio,
 * la sala espera a que los clientes se reconecten dentro del período de gracia.
 */
@Component
public class RoomConnectionRegistry {

    private final Map<Long, Map<Long, Integer>> connections = new ConcurrentHashMap<>();
    private final Map<Long, Instant> hostAbsentSince = new ConcurrentHashMap<>();

    public synchronized void register(Long roomId, Long userId) {
        connections.computeIfAbsent(roomId, key -> new ConcurrentHashMap<>())
                .merge(userId, 1, Integer::sum);
    }

    public synchronized void unregister(Long roomId, Long userId) {
        Map<Long, Integer> roomConnections = connections.get(roomId);
        if (roomConnections == null) {
            return;
        }
        roomConnections.computeIfPresent(userId, (key, count) -> count <= 1 ? null : count - 1);
        if (roomConnections.isEmpty()) {
            connections.remove(roomId);
        }
    }

    public boolean isConnected(Long roomId, Long userId) {
        Map<Long, Integer> roomConnections = connections.get(roomId);
        return roomConnections != null && roomConnections.getOrDefault(userId, 0) > 0;
    }

    public List<Long> connectedUserIds(Long roomId) {
        Map<Long, Integer> roomConnections = connections.get(roomId);
        return roomConnections == null ? List.of() : List.copyOf(roomConnections.keySet());
    }

    public Instant markHostAbsentIfNeeded(Long roomId, Instant now) {
        return hostAbsentSince.computeIfAbsent(roomId, key -> now);
    }

    public void clearHostAbsent(Long roomId) {
        hostAbsentSince.remove(roomId);
    }

    public void clearRoom(Long roomId) {
        connections.remove(roomId);
        hostAbsentSince.remove(roomId);
    }
}
