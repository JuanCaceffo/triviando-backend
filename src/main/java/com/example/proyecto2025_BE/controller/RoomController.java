package com.example.proyecto2025_BE.controller;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.model.room.Room;
import com.example.proyecto2025_BE.model.room.RoomAnswer;
import com.example.proyecto2025_BE.model.room.RoomCloseReason;
import com.example.proyecto2025_BE.model.room.RoomStatus;
import com.example.proyecto2025_BE.security.UserContext;
import com.example.proyecto2025_BE.service.room.RoomEventBroker;
import com.example.proyecto2025_BE.service.room.RoomEventPublisher;
import com.example.proyecto2025_BE.service.room.RoomGameService;
import com.example.proyecto2025_BE.service.room.RoomReactionService;
import com.example.proyecto2025_BE.service.room.RoomService;
import com.example.proyecto2025_BE.service.room.RoomSnapshotMapper;
import com.example.proyecto2025_BE.views.rooms.AnswerAckResponse;
import com.example.proyecto2025_BE.views.rooms.AnswerRoomRequest;
import com.example.proyecto2025_BE.views.rooms.CreateRoomRequest;
import com.example.proyecto2025_BE.views.rooms.ReactionRequest;
import com.example.proyecto2025_BE.views.rooms.RoomCreatedResponse;
import com.example.proyecto2025_BE.views.rooms.RoomSnapshotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Clock;

@CrossOrigin(origins = "*")
@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
@Tag(name = "Room Controller", description = "API de salas de trivia en vivo")
public class RoomController {

    private final RoomService roomService;
    private final RoomGameService gameService;
    private final RoomSnapshotMapper snapshotMapper;
    private final RoomEventPublisher eventPublisher;
    private final RoomEventBroker eventBroker;
    private final RoomReactionService reactionService;
    private final UserContext userContext;
    private final Clock clock;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una sala de trivia en vivo a partir de un tema",
            description = "Exclusivo para usuarios PREMIUM. La generación de preguntas continúa en segundo plano.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Sala creada"),
            @ApiResponse(responseCode = "403", description = Exceptions.PREMIUM_ONLY_ROOM_CREATION, content = @Content),
            @ApiResponse(responseCode = "409", description = Exceptions.ROOM_ALREADY_ACTIVE, content = @Content)
    })
    public ResponseEntity<RoomCreatedResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        Room room = roomService.createTopicRoom(
                request.topic(), userContext.getContextUserId(), userContext.getContextRole());
        RoomCreatedResponse body = new RoomCreatedResponse(room.getCode(), room.getStatus());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/rooms/{code}")
                .buildAndExpand(room.getCode())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una sala de trivia en vivo a partir de un archivo",
            description = "Exclusivo para usuarios PREMIUM. Acepta PDF, DOCX, TXT o MD de hasta 10 MB.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Sala creada"),
            @ApiResponse(responseCode = "400", description = Exceptions.INVALID_FILE_TYPE, content = @Content),
            @ApiResponse(responseCode = "403", description = Exceptions.PREMIUM_ONLY_ROOM_CREATION, content = @Content),
            @ApiResponse(responseCode = "409", description = Exceptions.ROOM_ALREADY_ACTIVE, content = @Content)
    })
    public ResponseEntity<RoomCreatedResponse> createFromFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("topic") String topic) {
        Room room = roomService.createFileRoom(
                topic, file, userContext.getContextUserId(), userContext.getContextRole());
        RoomCreatedResponse body = new RoomCreatedResponse(room.getCode(), room.getStatus());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/rooms/{code}")
                .buildAndExpand(room.getCode())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PostMapping("/{code}/join")
    @Operation(summary = "Une al usuario autenticado a una sala; es idempotente para reconexiones")
    public RoomSnapshotResponse join(@PathVariable String code) {
        Long userId = userContext.getContextUserId();
        roomService.joinRoom(code, userId);
        Room room = roomService.getByCode(code);
        eventPublisher.publishSnapshot(room.getId());
        return snapshotMapper.toSnapshot(room, userId);
    }

    @GetMapping("/active")
    @Operation(summary = "Devuelve la sala visible del usuario, si tiene una")
    public ResponseEntity<RoomSnapshotResponse> active() {
        Long userId = userContext.getContextUserId();
        Room room = roomService.findActiveRoomForUser(userId);
        if (room == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(snapshotMapper.toSnapshot(room, userId));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Obtiene el snapshot de la sala para un miembro")
    public RoomSnapshotResponse get(@PathVariable String code) {
        Long userId = userContext.getContextUserId();
        roomService.requireMember(code, userId);
        return snapshotMapper.toSnapshot(roomService.getByCode(code), userId);
    }

    @PostMapping("/{code}/start")
    @Operation(summary = "Inicia la partida; solo el anfitrión y con al menos dos jugadores")
    public RoomSnapshotResponse start(@PathVariable String code) {
        Long userId = userContext.getContextUserId();
        Room room = roomService.startRoom(code, userId);
        eventPublisher.publishSnapshot(room.getId());
        return snapshotMapper.toSnapshot(room, userId);
    }

    @PostMapping("/{code}/answers")
    @Operation(summary = "Responde la pregunta actual",
            description = "Devuelve solo la confirmación: el acierto y los puntos se revelan al cerrar la ronda.")
    public AnswerAckResponse answer(
            @PathVariable String code,
            @Valid @RequestBody AnswerRoomRequest request) {
        Long userId = userContext.getContextUserId();
        RoomAnswer answer = gameService.answer(code, userId, request.questionId(), request.option());
        eventPublisher.publishSnapshot(answer.getRoomId());
        long answeredAt = answer.getAnsweredAt()
                .atZone(clock.getZone()).toInstant().toEpochMilli();
        return new AnswerAckResponse(true, answer.getOptionSelected(), answeredAt);
    }

    @GetMapping(value = "/{code}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream SSE con el estado de la sala",
            description = "Envía un snapshot inicial y luego un snapshot por cada cambio de estado.")
    public ResponseEntity<SseEmitter> events(@PathVariable String code) {
        SseEmitter emitter = eventBroker.subscribe(code, userContext.getContextUserId());
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @PostMapping("/{code}/reactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Envía una reacción emoji efímera a la sala")
    public void reaction(
            @PathVariable String code,
            @Valid @RequestBody ReactionRequest request) {
        reactionService.react(code, userContext.getContextUserId(), request.emoji());
    }

    @PostMapping("/{code}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Abandona la sala; si es el anfitrión, la sala se cierra")
    public void leave(@PathVariable String code) {
        Long userId = userContext.getContextUserId();
        Room before = roomService.getByCode(code);
        boolean host = before.getHostUserId().equals(userId);
        roomService.leaveRoom(code, userId);
        Room after = roomService.getByCode(code);
        eventBroker.disconnectUser(after.getId(), userId);
        if (host && (RoomStatus.CLOSED.equals(after.getStatus())
                || RoomStatus.FAILED.equals(after.getStatus()))) {
            eventPublisher.publishClosed(after.getId(), RoomCloseReason.HOST_LEFT);
        } else {
            eventPublisher.publishSnapshot(after.getId());
        }
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cierra la sala; solo el anfitrión")
    public void close(@PathVariable String code) {
        Room room = roomService.getByCode(code);
        roomService.closeRoom(code, userContext.getContextUserId());
        eventPublisher.publishClosed(room.getId(), RoomCloseReason.HOST_CLOSED);
    }
}
