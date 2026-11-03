package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.room.RoomGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomGenerationRepository extends JpaRepository<RoomGeneration, Long> {

    Optional<RoomGeneration> findByRoomId(Long roomId);

    void deleteByRoomId(Long roomId);
}
