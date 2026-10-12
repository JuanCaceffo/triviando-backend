package com.example.proyecto2025_BE.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.proyecto2025_BE.model.PasswordResetToken;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findFirstByUserIdAndUsedFalse(Long userId);

    Optional<PasswordResetToken> findFirstByResetTokenAndUsedFalse(String resetToken);

    /** Borra todos los tokens del usuario en SQL inmediato, antes de crear el nuevo. */
    @Modifying(clearAutomatically = true)
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
