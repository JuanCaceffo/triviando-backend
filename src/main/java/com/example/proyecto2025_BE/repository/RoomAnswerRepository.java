package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.room.RoomAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomAnswerRepository extends JpaRepository<RoomAnswer, Long> {

    Optional<RoomAnswer> findByQuestionIdAndUserId(Long questionId, Long userId);

    List<RoomAnswer> findByRoomIdAndQuestionId(Long roomId, Long questionId);

    long countByRoomIdAndQuestionId(Long roomId, Long questionId);

    long countByRoomId(Long roomId);

    void deleteByRoomId(Long roomId);

    @Query("SELECT COUNT(DISTINCT a.userId) FROM RoomAnswer a WHERE a.roomId = :roomId")
    long countDistinctUsersByRoomId(@Param("roomId") Long roomId);

    @Query("""
            SELECT COUNT(a)
            FROM RoomAnswer a
            WHERE a.roomId = :roomId
              AND a.questionId = :questionId
              AND a.userId IN :userIds
            """)
    long countByQuestionAndUserIds(
            @Param("roomId") Long roomId,
            @Param("questionId") Long questionId,
            @Param("userIds") Collection<Long> userIds);
}
