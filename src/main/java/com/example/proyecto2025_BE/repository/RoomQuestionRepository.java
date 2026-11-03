package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.room.RoomQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomQuestionRepository extends JpaRepository<RoomQuestion, Long> {

    List<RoomQuestion> findByRoomIdOrderByPositionAsc(Long roomId);

    Optional<RoomQuestion> findByRoomIdAndPosition(Long roomId, int position);
}
