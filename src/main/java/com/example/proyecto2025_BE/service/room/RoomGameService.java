package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.ForbiddenException;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.LetterOption;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomAnswer;
import com.example.proyecto2025_BE.model.room.RoomMember;
import com.example.proyecto2025_BE.model.room.RoomOption;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomAnswerRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reglas de la partida: responder, cerrar rondas, revelar y avanzar.
 * Se implementa por slices TDD.
 */
@Service
@RequiredArgsConstructor
public class RoomGameService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomQuestionRepository questionRepository;
    private final RoomAnswerRepository answerRepository;
    private static final int BASE_POINTS = 100;
    private static final int SPEED_BONUS_MAX = 100;

    private final RoomConnectionRegistry connectionRegistry;
    private final RoomProperties properties;
    private final Clock clock;

    @Transactional
    public RoomAnswer answer(String code, Long userId, Long questionId, LetterOption option) {
        Room room = roomRepository.findByCodeForUpdate(normalizeCode(code))
                .orElseThrow(() -> NotFoundException.build(Exceptions.ROOM_NOT_FOUND));

        RoomMember member = memberRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> ForbiddenException.build(Exceptions.ROOM_NOT_MEMBER));
        if (member.getLeftAt() != null) {
            throw ForbiddenException.build(Exceptions.ROOM_NOT_MEMBER);
        }
        if (questionId == null || option == null) {
            throw ValidationException.build(Exceptions.ROOM_OPTION_INVALID);
        }

        // Idempotencia: un reintento de red con la misma opción devuelve el ack
        // aunque la ronda ya se haya cerrado.
        RoomAnswer existing = answerRepository.findByQuestionIdAndUserId(questionId, userId).orElse(null);
        if (existing != null && room.getId().equals(existing.getRoomId())) {
            if (option.equals(existing.getOptionSelected())) {
                return existing;
            }
            throw ConflictException.build(Exceptions.ROOM_ANSWER_ALREADY_SENT);
        }

        if (!RoomStatus.IN_PROGRESS.equals(room.getStatus()) || room.getRevealUntil() != null) {
            throw ConflictException.build(Exceptions.ROOM_ANSWER_WINDOW_CLOSED);
        }

        RoomQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> NotFoundException.build(Exceptions.ROOM_QUESTION_NOT_FOUND));
        if (!room.getId().equals(question.getRoomId())
                || question.getPosition() != room.getCurrentQuestionIndex()) {
            throw ConflictException.build(Exceptions.ROOM_QUESTION_NOT_CURRENT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (room.getQuestionDeadlineAt() == null || now.isAfter(room.getQuestionDeadlineAt())) {
            throw ConflictException.build(Exceptions.ROOM_ANSWER_WINDOW_CLOSED);
        }

        RoomOption selected = question.getOptions().stream()
                .filter(candidate -> option.equals(candidate.getLetter()))
                .findFirst()
                .orElseThrow(() -> ValidationException.build(Exceptions.ROOM_OPTION_INVALID));

        long roundMs = Math.max(1L, properties.getRoundSeconds() * 1000L);
        long elapsedMs = Math.max(0L, Math.min(
                Duration.between(room.getQuestionStartedAt(), now).toMillis(), roundMs));
        long remainingMs = Math.max(0L, Math.min(
                Duration.between(now, room.getQuestionDeadlineAt()).toMillis(), roundMs));
        int points = selected.isCorrect()
                ? BASE_POINTS + (int) Math.round((double) SPEED_BONUS_MAX * remainingMs / roundMs)
                : 0;

        RoomAnswer answer = RoomAnswer.builder()
                .roomId(room.getId())
                .questionId(question.getId())
                .userId(userId)
                .optionSelected(option)
                .millisecondsSpent(elapsedMs)
                .correct(selected.isCorrect())
                .points(points)
                .answeredAt(now)
                .build();
        return answerRepository.save(answer);
    }

    /**
     * Avanza el estado de una sala cuando corresponde. Devuelve true si hubo
     * una transición (cierre de ronda, avance o finalización).
     */
    @Transactional
    public boolean tickRoom(Long roomId) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> NotFoundException.build(Exceptions.ROOM_NOT_FOUND));
        if (!RoomStatus.IN_PROGRESS.equals(room.getStatus())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (room.getRevealUntil() != null) {
            if (now.isBefore(room.getRevealUntil())) {
                return false;
            }
            advance(room, now);
            return true;
        }

        if (room.getQuestionDeadlineAt() != null
                && now.isBefore(room.getQuestionDeadlineAt())
                && !allActiveMembersAnswered(room)) {
            return false;
        }

        closeRound(room, now);
        return true;
    }

    private boolean allActiveMembersAnswered(Room room) {
        List<RoomMember> activeMembers = activeMembers(room);
        if (activeMembers.isEmpty()) {
            return false;
        }
        RoomQuestion current = currentQuestion(room);
        if (current == null) {
            return false;
        }
        List<Long> connected = connectionRegistry.connectedUserIds(room.getId());
        List<Long> userIds = connected.isEmpty()
                ? activeMembers.stream().map(RoomMember::getUserId).toList()
                : activeMembers.stream()
                        .map(RoomMember::getUserId)
                        .filter(connected::contains)
                        .toList();
        if (userIds.isEmpty()) {
            return false;
        }
        long answered = answerRepository.countByQuestionAndUserIds(
                room.getId(), current.getId(), userIds);
        return answered >= userIds.size();
    }

    private void closeRound(Room room, LocalDateTime now) {
        if (room.getRevealUntil() != null) {
            return;
        }
        RoomQuestion current = currentQuestion(room);
        if (current == null) {
            return;
        }

        List<RoomAnswer> answers = answerRepository.findByRoomIdAndQuestionId(
                room.getId(), current.getId());
        // Incluye a los que salieron: si alcanzaron a responder, sus puntos cuentan.
        for (RoomMember member : memberRepository.findByRoomIdOrderByJoinedAtAsc(room.getId())) {
            RoomAnswer answer = answers.stream()
                    .filter(candidate -> member.getUserId().equals(candidate.getUserId()))
                    .findFirst()
                    .orElse(null);
            if (answer == null) {
                continue;
            }
            member.setScore(member.getScore() + answer.getPoints());
            if (answer.isCorrect()) {
                member.setCorrectCount(member.getCorrectCount() + 1);
                member.setTotalResponseMs(member.getTotalResponseMs() + answer.getMillisecondsSpent());
            }
            memberRepository.save(member);
        }

        room.setRevealUntil(now.plusSeconds(properties.getRevealSeconds()));
        roomRepository.save(room);
    }

    private void advance(Room room, LocalDateTime now) {
        int nextPosition = room.getCurrentQuestionIndex() + 1;
        if (nextPosition < room.getQuestionCount()) {
            room.setCurrentQuestionIndex(nextPosition);
            room.setQuestionStartedAt(now);
            room.setQuestionDeadlineAt(now.plusSeconds(properties.getRoundSeconds()));
            room.setRevealUntil(null);
            roomRepository.save(room);
            return;
        }

        room.setStatus(RoomStatus.FINISHED);
        room.setFinishedAt(now);
        room.setRevealUntil(null);
        room.setExpiresAt(now.plusMinutes(properties.getFinishedExpiryMinutes()));
        roomRepository.save(room);
    }

    private RoomQuestion currentQuestion(Room room) {
        return questionRepository.findByRoomIdAndPosition(room.getId(), room.getCurrentQuestionIndex())
                .orElse(null);
    }

    private List<RoomMember> activeMembers(Room room) {
        return memberRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .filter(member -> member.getLeftAt() == null)
                .toList();
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
