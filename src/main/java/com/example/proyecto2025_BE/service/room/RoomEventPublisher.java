package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.model.room.RoomCloseReason;

/**
 * Punto de publicación de eventos de sala. Lo implementa el broker SSE.
 */
public interface RoomEventPublisher {

    void publishSnapshot(Long roomId);

    void publishReaction(Long roomId, Long userId, String emoji);

    void publishClosed(Long roomId, RoomCloseReason reason);
}
