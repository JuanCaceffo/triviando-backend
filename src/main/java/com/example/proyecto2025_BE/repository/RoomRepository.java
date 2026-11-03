package com.example.proyecto2025_BE.repository;

import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.code = :code")
    Optional<Room> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCode(String code);

    boolean existsByHostUserIdAndStatusIn(Long hostUserId, Collection<RoomStatus> statuses);

    List<Room> findByStatusIn(Collection<RoomStatus> statuses);

    List<Room> findByStatusInAndExpiresAtBefore(
            Collection<RoomStatus> statuses, LocalDateTime cutoff);

    List<Room> findByExpiresAtBefore(LocalDateTime cutoff);
}
