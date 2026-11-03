package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.ForbiddenException;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomCloseReason;
import com.example.proyecto2025_BE.model.room.RoomMember;
import com.example.proyecto2025_BE.model.room.RoomSource;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomRepository;
import com.example.proyecto2025_BE.service.document.DocumentTextExtractionService;
import com.example.proyecto2025_BE.utils.ContentNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ciclo de vida de las salas. La implementación se construye por slices TDD.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    static final List<RoomStatus> ACTIVE_STATUSES = List.of(
            RoomStatus.PREPARING,
            RoomStatus.LOBBY,
            RoomStatus.IN_PROGRESS);

    static final List<RoomStatus> VISIBLE_STATUSES = List.of(
            RoomStatus.PREPARING,
            RoomStatus.LOBBY,
            RoomStatus.IN_PROGRESS,
            RoomStatus.FINISHED);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomGenerationService generationService;
    private final RoomCodeGenerator codeGenerator;
    private final RoomProperties properties;
    private final DocumentTextExtractionService textExtractionService;
    private final Clock clock;

    private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    @Transactional
    public Room createTopicRoom(String topic, Long hostUserId, Account role) {
        ensurePremium(role);
        String cleanTopic = normalizeTopic(topic);
        lockUser(hostUserId);
        ensureHostHasNoActiveRoom(hostUserId);
        return createRoom(cleanTopic, hostUserId, RoomSource.TOPIC, null);
    }

    @Transactional
    public Room createFileRoom(String topic, MultipartFile file, Long hostUserId, Account role) {
        ensurePremium(role);
        String cleanTopic = normalizeTopic(topic);
        lockUser(hostUserId);
        ensureHostHasNoActiveRoom(hostUserId);
        String extractedText = textExtractionService.extract(file);
        return createRoom(cleanTopic, hostUserId, RoomSource.FILE, extractedText);
    }

    private Room createRoom(String topic, Long hostUserId, RoomSource source, String extractedText) {
        LocalDateTime now = LocalDateTime.now(clock);
        Room room = roomRepository.save(Room.builder()
                .code(codeGenerator.generate())
                .hostUserId(hostUserId)
                .status(RoomStatus.PREPARING)
                .source(source)
                .topic(topic)
                .questionCount(0)
                .currentQuestionIndex(-1)
                .createdAt(now)
                .expiresAt(now.plusMinutes(properties.getPreparingExpiryMinutes()))
                .build());

        memberRepository.save(RoomMember.builder()
                .roomId(room.getId())
                .userId(hostUserId)
                .joinedAt(now)
                .lastSeenAt(now)
                .build());

        generationService.enqueue(room.getId(), extractedText);
        return room;
    }

    @Transactional
    public RoomMember joinRoom(String code, Long userId) {
        lockUser(userId);
        Room room = findByCodeForUpdateOrThrow(code);

        RoomMember existing = memberRepository.findByRoomIdAndUserId(room.getId(), userId).orElse(null);
        if (existing != null) {
            if (existing.getLeftAt() != null) {
                throw ConflictException.build(Exceptions.ROOM_LEFT);
            }
            return existing;
        }

        if (RoomStatus.IN_PROGRESS.equals(room.getStatus())) {
            throw ConflictException.build(Exceptions.ROOM_ALREADY_STARTED);
        }
        if (!RoomStatus.PREPARING.equals(room.getStatus())
                && !RoomStatus.LOBBY.equals(room.getStatus())) {
            throw ConflictException.build(Exceptions.ROOM_NOT_JOINABLE);
        }
        if (memberRepository.existsActiveMembershipInOtherRoom(userId, room.getId(), ACTIVE_STATUSES)) {
            throw ConflictException.build(Exceptions.ROOM_ALREADY_IN_ANOTHER);
        }
        if (memberRepository.countByRoomIdAndLeftAtIsNull(room.getId()) >= properties.getMaxPlayers()) {
            throw ConflictException.build(Exceptions.ROOM_FULL);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        return memberRepository.save(RoomMember.builder()
                .roomId(room.getId())
                .userId(userId)
                .joinedAt(now)
                .lastSeenAt(now)
                .build());
    }

    @Transactional
    public Room startRoom(String code, Long hostUserId) {
        Room room = findByCodeForUpdateOrThrow(code);
        if (!room.getHostUserId().equals(hostUserId)) {
            throw ForbiddenException.build(Exceptions.ROOM_ONLY_HOST);
        }
        if (!RoomStatus.LOBBY.equals(room.getStatus())) {
            throw ConflictException.build(Exceptions.ROOM_NOT_IN_LOBBY);
        }
        long memberCount = memberRepository.countByRoomIdAndLeftAtIsNull(room.getId());
        if (memberCount < properties.getMinPlayers()) {
            throw ConflictException.build(Exceptions.ROOM_MIN_PLAYERS);
        }
        if (room.getQuestionCount() < 1) {
            throw ConflictException.build(Exceptions.ROOM_NOT_READY);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        room.setStatus(RoomStatus.IN_PROGRESS);
        room.setCurrentQuestionIndex(0);
        room.setQuestionStartedAt(now);
        room.setQuestionDeadlineAt(now.plusSeconds(properties.getRoundSeconds()));
        room.setRevealUntil(null);
        room.setStartedAt(now);
        room.setExpiresAt(now.plusMinutes(properties.getInProgressExpiryMinutes()));
        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public Room getByCode(String code) {
        return findByCodeOrThrow(code);
    }

    @Transactional(readOnly = true)
    public RoomMember requireMember(String code, Long userId) {
        Room room = findByCodeOrThrow(code);
        RoomMember member = memberRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> ForbiddenException.build(Exceptions.ROOM_NOT_MEMBER));
        if (member.getLeftAt() != null) {
            throw ForbiddenException.build(Exceptions.ROOM_NOT_MEMBER);
        }
        return member;
    }

    @Transactional(readOnly = true)
    public Room findActiveRoomForUser(Long userId) {
        List<Long> roomIds = memberRepository.findVisibleRoomIds(userId, VISIBLE_STATUSES);
        if (roomIds.isEmpty()) {
            return null;
        }
        return roomRepository.findById(roomIds.get(0)).orElse(null);
    }

    @Transactional
    public void leaveRoom(String code, Long userId) {
        Room room = findByCodeForUpdateOrThrow(code);
        LocalDateTime now = LocalDateTime.now(clock);

        if (room.getHostUserId().equals(userId)) {
            if (RoomStatus.PREPARING.equals(room.getStatus())
                    || RoomStatus.LOBBY.equals(room.getStatus())
                    || RoomStatus.IN_PROGRESS.equals(room.getStatus())) {
                closeRoomInternal(room, RoomCloseReason.HOST_LEFT, now);
            } else {
                memberRepository.findByRoomIdAndUserId(room.getId(), userId)
                        .ifPresent(memberRepository::delete);
            }
            return;
        }

        RoomMember member = memberRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> ForbiddenException.build(Exceptions.ROOM_NOT_MEMBER));

        if (RoomStatus.IN_PROGRESS.equals(room.getStatus())) {
            member.setLeftAt(now);
            member.setLastSeenAt(now);
            memberRepository.save(member);
            return;
        }
        memberRepository.delete(member);
    }

    @Transactional
    public void closeRoom(String code, Long userId) {
        Room room = findByCodeForUpdateOrThrow(code);
        if (!room.getHostUserId().equals(userId)) {
            throw ForbiddenException.build(Exceptions.ROOM_ONLY_HOST);
        }
        closeRoomInternal(room, RoomCloseReason.HOST_CLOSED, LocalDateTime.now(clock));
    }

    @Transactional
    public void closeForHostAbsence(Long roomId, LocalDateTime now) {
        roomRepository.findByIdForUpdate(roomId).ifPresent(room -> {
            if (RoomStatus.PREPARING.equals(room.getStatus())
                    || RoomStatus.LOBBY.equals(room.getStatus())
                    || RoomStatus.IN_PROGRESS.equals(room.getStatus())) {
                closeRoomInternal(room, RoomCloseReason.HOST_LEFT, now);
            }
        });
    }

    @Transactional
    public void expireRoom(Long roomId, LocalDateTime now) {
        roomRepository.findByIdForUpdate(roomId).ifPresent(room -> {
            if (RoomStatus.CLOSED.equals(room.getStatus())
                    || RoomStatus.FAILED.equals(room.getStatus())
                    || RoomStatus.FINISHED.equals(room.getStatus())) {
                return;
            }
            room.setStatus(RoomStatus.CLOSED);
            room.setCloseReason(RoomCloseReason.EXPIRED);
            room.setRevealUntil(null);
            room.setExpiresAt(now.plusMinutes(properties.getClosedExpiryMinutes()));
            roomRepository.save(room);
        });
    }

    private void closeRoomInternal(Room room, RoomCloseReason reason, LocalDateTime now) {
        if (RoomStatus.CLOSED.equals(room.getStatus())
                || RoomStatus.FAILED.equals(room.getStatus())) {
            return;
        }
        room.setStatus(RoomStatus.CLOSED);
        room.setCloseReason(reason);
        room.setRevealUntil(null);
        room.setExpiresAt(now.plusMinutes(properties.getClosedExpiryMinutes()));
        roomRepository.save(room);
    }

    private Room findByCodeOrThrow(String code) {
        String cleanCode = code == null ? "" : code.trim().toUpperCase();
        return roomRepository.findByCode(cleanCode)
                .orElseThrow(() -> NotFoundException.build(Exceptions.ROOM_NOT_FOUND));
    }

    private Room findByCodeForUpdateOrThrow(String code) {
        String cleanCode = code == null ? "" : code.trim().toUpperCase();
        return roomRepository.findByCodeForUpdate(cleanCode)
                .orElseThrow(() -> NotFoundException.build(Exceptions.ROOM_NOT_FOUND));
    }

    /**
     * Serializa creación/unión por usuario hasta que termina la transacción.
     * Evita duplicar salas o membresías ante dobles clicks o requests paralelos.
     */
    private void lockUser(Long userId) {
        ReentrantLock lock = userLocks.computeIfAbsent(userId, key -> new ReentrantLock());
        lock.lock();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    lock.unlock();
                }
            });
        } else {
            lock.unlock();
        }
    }

    private void ensurePremium(Account role) {
        if (!Account.PREMIUM.equals(role)) {
            throw ForbiddenException.build(Exceptions.PREMIUM_ONLY_ROOM_CREATION);
        }
    }

    private String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw ValidationException.build(Exceptions.ROOM_TOPIC_REQUIRED);
        }
        String cleanTopic = ContentNormalizer.displayName(topic);
        if (cleanTopic.length() > ContentNormalizer.MAX_TOPIC_LENGTH) {
            throw ValidationException.build(Exceptions.ROOM_TOPIC_TOO_LONG);
        }
        return cleanTopic;
    }

    private void ensureHostHasNoActiveRoom(Long hostUserId) {
        if (roomRepository.existsByHostUserIdAndStatusIn(hostUserId, ACTIVE_STATUSES)) {
            throw ConflictException.build(Exceptions.ROOM_ALREADY_ACTIVE);
        }
    }
}
