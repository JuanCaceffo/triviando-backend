package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.room.RoomMember;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    List<RoomMember> findByRoomIdOrderByJoinedAtAsc(Long roomId);

    long countByRoomIdAndLeftAtIsNull(Long roomId);

    void deleteByRoomId(Long roomId);

    @Query("""
            SELECT COUNT(m) > 0
            FROM RoomMember m
            WHERE m.userId = :userId
              AND m.leftAt IS NULL
              AND m.roomId <> :roomId
              AND EXISTS (
                  SELECT 1 FROM Room r
                  WHERE r.id = m.roomId AND r.status IN :statuses
              )
            """)
    boolean existsActiveMembershipInOtherRoom(
            @Param("userId") Long userId,
            @Param("roomId") Long roomId,
            @Param("statuses") Collection<RoomStatus> statuses);

    @Query("""
            SELECT m.roomId
            FROM RoomMember m
            WHERE m.userId = :userId
              AND m.leftAt IS NULL
              AND EXISTS (
                  SELECT 1 FROM Room r
                  WHERE r.id = m.roomId AND r.status IN :statuses
              )
            ORDER BY m.joinedAt DESC
            """)
    List<Long> findVisibleRoomIds(
            @Param("userId") Long userId,
            @Param("statuses") Collection<RoomStatus> statuses);
}
