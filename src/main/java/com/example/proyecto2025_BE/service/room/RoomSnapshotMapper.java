package com.example.proyecto2025_BE.service.room;

import com.example.proyecto2025_BE.configuration.RoomProperties;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomAnswer;
import com.example.proyecto2025_BE.model.room.RoomMember;
import com.example.proyecto2025_BE.model.room.RoomOption;
import com.example.proyecto2025_BE.model.room.RoomQuestion;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.repository.RoomAnswerRepository;
import com.example.proyecto2025_BE.repository.RoomGenerationRepository;
import com.example.proyecto2025_BE.repository.RoomMemberRepository;
import com.example.proyecto2025_BE.repository.RoomQuestionRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.views.rooms.MeResponse;
import com.example.proyecto2025_BE.views.rooms.RoomGenerationResponse;
import com.example.proyecto2025_BE.views.rooms.RoomHostResponse;
import com.example.proyecto2025_BE.views.rooms.RoomOptionResponse;
import com.example.proyecto2025_BE.views.rooms.RoomPlayerResponse;
import com.example.proyecto2025_BE.views.rooms.RoomQuestionResponse;
import com.example.proyecto2025_BE.views.rooms.RoomSnapshotResponse;
import com.example.proyecto2025_BE.views.rooms.TeamSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Construye el snapshot que consume la UI. Es el único lugar donde se decide
 * qué se muestra antes y después del reveal: la opción correcta y la
 * explicación solo salen cuando {@code revealUntil} está activo.
 */
@Service
@RequiredArgsConstructor
public class RoomSnapshotMapper {

    private final RoomMemberRepository memberRepository;
    private final RoomQuestionRepository questionRepository;
    private final RoomAnswerRepository answerRepository;
    private final RoomGenerationRepository generationRepository;
    private final UserRepository userRepository;
    private final RoomConnectionRegistry connectionRegistry;
    private final RoomProperties properties;
    private final Clock clock;

    public RoomSnapshotResponse toSnapshot(Room room, Long viewerUserId) {
        List<RoomMember> members = memberRepository.findByRoomIdOrderByJoinedAtAsc(room.getId());

        Set<Long> userIds = new HashSet<>();
        userIds.add(room.getHostUserId());
        members.forEach(member -> userIds.add(member.getUserId()));
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        RoomQuestion current = currentQuestion(room);
        Map<Long, RoomAnswer> answersByUser = current == null
                ? Map.of()
                : answerRepository.findByRoomIdAndQuestionId(room.getId(), current.getId()).stream()
                        .collect(Collectors.toMap(RoomAnswer::getUserId, Function.identity(), (a, b) -> a));

        boolean reveal = room.getRevealUntil() != null;

        List<PlayerData> ranked = members.stream()
                .map(member -> new PlayerData(member, users.get(member.getUserId()),
                        answersByUser.get(member.getUserId())))
                .sorted(playerComparator())
                .toList();

        List<RoomPlayerResponse> players = IntStream.range(0, ranked.size())
                .mapToObj(index -> toPlayer(ranked.get(index), index + 1, room, reveal))
                .toList();

        PlayerData viewer = ranked.stream()
                .filter(player -> player.member().getUserId().equals(viewerUserId))
                .findFirst()
                .orElse(null);

        return new RoomSnapshotResponse(
                room.getCode(),
                room.getStatus(),
                room.getTopic(),
                room.getSource(),
                host(users.get(room.getHostUserId())),
                players,
                properties.getMaxPlayers(),
                room.getQuestionCount(),
                properties.getRoundSeconds(),
                properties.getRevealSeconds(),
                currentQuestionNumber(room),
                question(room, current, reveal),
                epoch(room.getQuestionDeadlineAt()),
                epoch(room.getRevealUntil()),
                clock.millis(),
                generation(room),
                me(viewer, ranked, room, reveal),
                teamSummary(room, players),
                room.getCloseReason());
    }

    private RoomQuestion currentQuestion(Room room) {
        if (!RoomStatus.IN_PROGRESS.equals(room.getStatus()) || room.getCurrentQuestionIndex() < 0) {
            return null;
        }
        return questionRepository
                .findByRoomIdAndPosition(room.getId(), room.getCurrentQuestionIndex())
                .orElse(null);
    }

    private Integer currentQuestionNumber(Room room) {
        if (!RoomStatus.IN_PROGRESS.equals(room.getStatus()) || room.getCurrentQuestionIndex() < 0) {
            return null;
        }
        return room.getCurrentQuestionIndex() + 1;
    }

    private RoomQuestionResponse question(Room room, RoomQuestion current, boolean reveal) {
        if (current == null) {
            return null;
        }
        List<RoomOptionResponse> options = current.getOptions().stream()
                .map(option -> new RoomOptionResponse(option.getLetter(), option.getText()))
                .toList();
        RoomOption correct = reveal
                ? current.getOptions().stream().filter(RoomOption::isCorrect).findFirst().orElse(null)
                : null;
        return new RoomQuestionResponse(
                current.getId(),
                room.getCurrentQuestionIndex() + 1,
                current.getEnunciado(),
                options,
                correct == null ? null : correct.getLetter().name(),
                correct == null ? null : correct.getText(),
                reveal ? current.getExplicacion() : null);
    }

    private RoomPlayerResponse toPlayer(PlayerData data, int rank, Room room, boolean reveal) {
        RoomAnswer answer = data.answer();
        return new RoomPlayerResponse(
                data.member().getUserId(),
                username(data.user()),
                displayName(data.user()),
                data.member().getScore(),
                data.member().getCorrectCount(),
                data.member().getTotalResponseMs(),
                answer != null,
                data.member().getUserId().equals(room.getHostUserId()),
                data.member().getLeftAt() != null,
                connectionRegistry.isConnected(room.getId(), data.member().getUserId()),
                reveal && answer != null ? answer.getPoints() : null,
                reveal && answer != null ? answer.isCorrect() : null,
                rank);
    }

    private MeResponse me(PlayerData viewer, List<PlayerData> ranked, Room room, boolean reveal) {
        if (viewer == null) {
            return null;
        }
        int rank = ranked.indexOf(viewer) + 1;
        RoomAnswer answer = viewer.answer();
        return new MeResponse(
                viewer.member().getUserId(),
                viewer.member().getUserId().equals(room.getHostUserId()),
                answer != null,
                answer == null ? null : answer.getOptionSelected(),
                reveal && answer != null ? answer.getPoints() : null,
                reveal && answer != null ? answer.isCorrect() : null,
                rank);
    }

    private TeamSummaryResponse teamSummary(Room room, List<RoomPlayerResponse> players) {
        if (!RoomStatus.FINISHED.equals(room.getStatus())) {
            return null;
        }
        int totalCorrect = players.stream().mapToInt(RoomPlayerResponse::correctCount).sum();
        int totalAnswers = Math.toIntExact(answerRepository.countByRoomId(room.getId()));
        int participants = Math.toIntExact(answerRepository.countDistinctUsersByRoomId(room.getId()));
        double accuracy = totalAnswers == 0 ? 0.0 : (double) totalCorrect / totalAnswers;
        return new TeamSummaryResponse(
                totalCorrect,
                totalAnswers,
                room.getQuestionCount(),
                participants,
                accuracy,
                players.isEmpty() ? null : players.get(0));
    }

    private RoomGenerationResponse generation(Room room) {
        return generationRepository.findByRoomId(room.getId())
                .map(generation -> new RoomGenerationResponse(
                        generation.getStatus(), generation.getProgress(), generation.getError()))
                .orElse(null);
    }

    private RoomHostResponse host(User user) {
        if (user == null) {
            return null;
        }
        return new RoomHostResponse(user.getId(), username(user), displayName(user));
    }

    private Comparator<PlayerData> playerComparator() {
        return Comparator
                .comparingInt((PlayerData data) -> data.member().getScore()).reversed()
                .thenComparing(Comparator.comparingInt(
                        (PlayerData data) -> data.member().getCorrectCount()).reversed())
                .thenComparingLong(data -> data.member().getTotalResponseMs())
                .thenComparing(data -> data.member().getJoinedAt(),
                        Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private Long epoch(LocalDateTime value) {
        return value == null ? null : value.atZone(clock.getZone()).toInstant().toEpochMilli();
    }

    private String username(User user) {
        return user == null || user.getUsername() == null ? "" : user.getUsername();
    }

    private String displayName(User user) {
        if (user == null) {
            return "Jugador";
        }
        String name = Stream.of(user.getName(), user.getLastName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        return name.isBlank() ? username(user) : name;
    }

    private record PlayerData(RoomMember member, User user, RoomAnswer answer) {
    }
}
