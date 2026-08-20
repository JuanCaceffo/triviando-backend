package com.example.proyecto2025_BE.repository;

import java.util.Optional;

import com.example.proyecto2025_BE.utils.UserRankingProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.proyecto2025_BE.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long>{

	public Optional<User> findByEmailAndPassword(String email, String password);

	public Optional<User> findByEmail(String email);

	@Query(value =
			"SELECT position FROM (" +
					"  SELECT u.id, SUM(a.score) as total_score, ROW_NUMBER() OVER (ORDER BY SUM(a.score) DESC, u.id ASC) as position " +
					"  FROM users u LEFT JOIN answer a ON u.id = a.user_id " +
					"  GROUP BY u.id " +
					") ranked WHERE ranked.id = :userId",
			nativeQuery = true)
	Integer findUserRankPosition(@Param("userId") Long userId);

	@Query(value = """
       SELECT u.id as id, u.username as username, COALESCE(SUM(a.score), 0) as score, ranked.position as position
       FROM (
          SELECT u_inner.id, COALESCE(SUM(a_inner.score), 0) as total_score,
                ROW_NUMBER() OVER (ORDER BY COALESCE(SUM(a_inner.score), 0) DESC, u_inner.id ASC) as position
          FROM users u_inner
          LEFT JOIN answer a_inner ON u_inner.id = a_inner.user_id
          GROUP BY u_inner.id
       ) ranked
       JOIN users u ON u.id = ranked.id
       LEFT JOIN answer a ON u.id = a.user_id
       GROUP BY u.id, u.username, ranked.position
       ORDER BY ranked.position ASC, u.id ASC
       """,
			countQuery = "SELECT COUNT(DISTINCT u.id) FROM users u LEFT JOIN answer a ON u.id = a.user_id",
			nativeQuery = true)
	Page<UserRankingProjection> findAllUsersWithRank(Pageable pageable);

	@Query(value = """
    SELECT ranked.position
    FROM (
        SELECT 
            u_inner.id,
            ROW_NUMBER() OVER (
                ORDER BY COALESCE(SUM(a_inner.score), 0) DESC, u_inner.id ASC
            ) AS position
        FROM users u_inner
        LEFT JOIN answer a_inner 
            ON u_inner.id = a_inner.user_id
            AND a_inner.response_date >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
            AND a_inner.response_date <  DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 7 DAY)
        GROUP BY u_inner.id
    ) ranked
    WHERE ranked.id = :userId
    """, nativeQuery = true)
	Integer findUserRankWeeklyPosition(@Param("userId") Long userId);

	@Query(value = """
    SELECT 
        ranked.id AS id,
        u.username AS username,
        ranked.total_score AS score,
        ranked.position AS position
    FROM (
        SELECT 
            u_inner.id,
            COALESCE(SUM(a_inner.score), 0) AS total_score,
            ROW_NUMBER() OVER (
                ORDER BY COALESCE(SUM(a_inner.score), 0) DESC, u_inner.id ASC
            ) AS position
        FROM users u_inner
        LEFT JOIN answer a_inner 
            ON u_inner.id = a_inner.user_id
            AND a_inner.response_date >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
            AND a_inner.response_date <  DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 7 DAY)
        GROUP BY u_inner.id
    ) ranked
    JOIN users u ON u.id = ranked.id
    ORDER BY ranked.total_score DESC, ranked.id ASC
    """,
			countQuery = """
        SELECT COUNT(*) 
        FROM users
    """,
			nativeQuery = true)
	Page<UserRankingProjection> findWeeklyRanking(Pageable pageable);

	Optional<User> findByUsername(String username);

}
