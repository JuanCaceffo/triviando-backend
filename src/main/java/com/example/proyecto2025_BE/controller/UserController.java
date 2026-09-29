package com.example.proyecto2025_BE.controller;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.Ranking;
import com.example.proyecto2025_BE.model.dto.StatsResponse;
import com.example.proyecto2025_BE.model.dto.login.LoginRequestDTO;
import com.example.proyecto2025_BE.model.dto.login.LoginResponseDTO;
import com.example.proyecto2025_BE.service.UserService;
import com.example.proyecto2025_BE.views.Views;
import com.example.proyecto2025_BE.views.users.UserResponse4XX;
import com.example.proyecto2025_BE.views.users.get.GetUserResponse;
import com.example.proyecto2025_BE.views.users.login.request.UserLoginRequest;
import com.example.proyecto2025_BE.views.users.racha.UserRachaResponse;
import com.example.proyecto2025_BE.views.users.ranking.PageUserRankingResponse;
import com.example.proyecto2025_BE.views.users.register.request.UserRegisterRequest;
import com.example.proyecto2025_BE.views.users.score.UserScoreResponse;
import com.example.proyecto2025_BE.views.users.update.request.UpdateUserRequest;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name = "User Controller", description = "API para la gestión de usuarios")
public class UserController {
    private final UserService userService;

    @PostMapping
        @SecurityRequirements
    @Operation(summary = "Registrar usuario", description = "Se registra un nuevo usuario en el sistema")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Datos del usuario para el registro",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UserRegisterRequest.class)))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "El usuario ya existe en el sistema",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    public ResponseEntity<LoginResponseDTO> create(@RequestBody
                                                   @Validated(Views.RegisterRequest.class) User user) {
        LoginResponseDTO loginResponseDTO = this.userService.create(user);
        return ResponseEntity.status(201).body(loginResponseDTO);
    }

    @GetMapping("/{id}")
    @JsonView(Views.GetUser.class)
    @Operation(summary = "Obtener usuario", description = "Obtiene los detalles de un usuario por ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GetUserResponse.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    public ResponseEntity<User> retrieve(@PathVariable Long id) {
        User user = this.userService.retrieve(id);

        return ResponseEntity.ok(user);
    }

    @PutMapping
    @JsonView(Views.UpdateUser.class)
    @Operation(summary = "Actualizar usuario", description = "Actualiza los detalles de un usuario existente")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Datos del usuario para la actualización",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UpdateUserRequest.class)))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario actualizado exitosamente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
        public ResponseEntity<LoginResponseDTO> update(@RequestBody User user) {
                LoginResponseDTO loginResponseDTO = this.userService.update(user);

                return ResponseEntity.ok(loginResponseDTO);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar usuario", description = "Elimina un usuario por ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario eliminado exitosamente"),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND)})
    public ResponseEntity<String> delete(@PathVariable Long id) {
        this.userService.delete(id);
        return ResponseEntity.ok(Strings.EMPTY);
    }

    @PostMapping("/login")
        @SecurityRequirements
    @Operation(summary = "Login de usuario", description = "Autentica a un usuario basado en su nombre de usuario y contraseña")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Credenciales del usuario para el login", required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UserLoginRequest.class)))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario autenticado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales invalidas",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Validated(Views.LoginRequest.class) LoginRequestDTO user) {

        LoginResponseDTO loginResponseDTO = this.userService.login(user);
        return ResponseEntity.status(200).body(loginResponseDTO);
    }

    @GetMapping("/statistics/{usuarioId}")
    @Operation(summary = "Obtener estadísticas de un usuario", description = "Obtiene las estadísticas de un usuario por ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StatsResponse.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    public ResponseEntity<StatsResponse> getStatistics(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(userService.getStatisticsFromUser(usuarioId));
    }

    @GetMapping("/score/{userId}")
    @JsonView(Views.Score.class)
    @Operation(summary = "Obtener puntaje de un usuario", description = "Obtiene el puntaje de un usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Puntaje encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserScoreResponse.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    public ResponseEntity<User> retrieveScore(@PathVariable Long userId) {
        User user = this.userService.retrieve(userId);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/ranking")
    @JsonView(Views.Ranking.class)
    @Operation(summary = "Obtener ranking de usuarios", description = "Obtiene el ranking de usuarios")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ranking obtenido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageUserRankingResponse.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    ResponseEntity<Page<User>> getUsersOrderedByScoreDesc(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.getUsersOrderedByScoreDesc(page, size));
    }

    @GetMapping("/ranking-semanal")
    @Operation(summary = "Ranking de usuarios", description = "Devuelve el ranking semanal de usuarios")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ranking semanal obtenido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageUserRankingResponse.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    public ResponseEntity<Page<Ranking>> getWeeklyRanking(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.getWeeklyRanking(page, size));
    }

    @GetMapping("/ranking-semanal/{userId}")
    @Operation(summary = "Ranking de usuarios", description = "Devuelve el ranking semanal de usuarios")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ranking semanal obtenido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageUserRankingResponse.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    public ResponseEntity<Page<Ranking>> getWeeklyRankingByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.getWeeklyRanking(userId, page, size));
    }

    @GetMapping("/ranking/{userId}")
    @JsonView(Views.Ranking.class)
    @Operation(summary = "Obtener ranking del usuario", description = "Obtiene el ranking del usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ranking obtenido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageUserRankingResponse.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    ResponseEntity<Page<User>> getUsersOrderedByScoreFromUser(@PathVariable Long userId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size, @
                                                                      RequestParam(required = false) String[] sort) {
        return ResponseEntity.ok(userService.getUsersOrderedByScoreFromUser(userId, page, size));
    }

    @GetMapping("/racha/{userId}")
    @JsonView(Views.Racha.class)
    @Operation(summary = "Obtener racha de un usuario", description = "Obtiene la racha de un usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Racha obtenida",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserRachaResponse.class))),
            @ApiResponse(responseCode = "404", description = Exceptions.NOT_FOUND,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse4XX.class)))})
    public ResponseEntity<User> getRachaUsuario(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.retrieve(userId));
    }
}