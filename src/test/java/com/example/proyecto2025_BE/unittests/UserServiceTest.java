package com.example.proyecto2025_BE.unittests;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.repository.ResponseRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.StatsResponse;
import com.example.proyecto2025_BE.model.dto.login.LoginRequestDTO;
import com.example.proyecto2025_BE.model.dto.login.LoginResponseDTO;
import com.example.proyecto2025_BE.security.JwtUtil;
import com.example.proyecto2025_BE.service.UserService;
import com.example.proyecto2025_BE.utils.UserRankingProjection;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResponseRepository answerDao;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private JwtUtil jwtUtils;

    @InjectMocks
    private UserService userService;

    private List<User> users;
    private List<User> createUsers() {
        return Arrays.asList(
                User.builder()
                        .id(1L)
                        .username("Usuario Uno")
                        .score(new BigDecimal("100.00"))
                        .build(),
                User.builder()
                        .id(2L)
                        .username("Usuario Dos")
                        .score(new BigDecimal("200.00"))
                        .build(),
                User.builder()
                        .id(3L)
                        .username("Usuario Tres")
                        .score(new BigDecimal("300.00"))
                        .build(),
                User.builder()
                        .id(4L)
                        .username("Usuario Cuatro")
                        .score(new BigDecimal("400.00"))
                        .build(),
                User.builder()
                        .id(5L)
                        .username("Usuario Cinco")
                        .score(new BigDecimal("500.00"))
                        .build(),
                User.builder()
                        .id(6L)
                        .username("Usuario Seis")
                        .score(new BigDecimal("600.00"))
                        .build(),
                User.builder()
                        .id(7L)
                        .username("Usuario Siete")
                        .score(new BigDecimal("700.00"))
                        .build(),
                User.builder()
                        .id(8L)
                        .username("Usuario Ocho")
                        .score(new BigDecimal("800.00"))
                        .build(),
                User.builder()
                        .id(9L)
                        .username("Usuario Nueve")
                        .score(new BigDecimal("900.00"))
                        .build(),
                User.builder()
                        .id(10L)
                        .username("Usuario Diez")
                        .score(new BigDecimal("1000.00"))
                        .build(),
                User.builder()
                        .id(11L)
                        .username("Usuario Once")
                        .score(new BigDecimal("200.00"))
                        .build(),
                User.builder()
                        .id(12L)
                        .username("Usuario Doce")
                        .score(new BigDecimal("300.00"))
                        .build());
    }


    @BeforeEach
    void setUp() {
        users = createUsers();
        getSortedUsers().forEach(u -> when(userRepository.findById(u.getId())).thenReturn(Optional.of(u)));
    }
    @AfterEach
    void tearDown() {
        Mockito.reset(userRepository);
    }

    private List<User> getSortedUsers() {
        return users.stream()
                .sorted(Comparator.comparing(User::getScore, Comparator.reverseOrder())
                        .thenComparing(User::getId))
                .toList();
    }

    private int findUserPosition(Long userId) {
        List<User> sortedUsers = getSortedUsers();
        for (int i = 0; i < sortedUsers.size(); i++) {
            if (sortedUsers.get(i).getId().equals(userId)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void getUsersOrderedByScoreDesc_ShouldReturnPageOfUsersOrderedByScore() {
        Pageable pageable = PageRequest.of(0, 5);

        List<User> sortedUsers = getSortedUsers();
        List<UserRankingProjection> projections = new ArrayList<>();

        // Crear las proyecciones para los primeros 5 usuarios
        sortedUsers.stream()
                .limit(5)
                .forEach(u -> {
                    int position = findUserPosition(u.getId()) + 1;
                    projections.add(new UserRankingProjectionImpl(u.getId(), u.getUsername(), u.getScore(), position));
                    when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
                });

        Page<UserRankingProjection> projectionPage = new PageImpl<>(projections, pageable, sortedUsers.size());

        when(userRepository.findAllUsersWithRank(pageable)).thenReturn(projectionPage);

        Page<User> result = userService.getUsersOrderedByScoreDesc(pageable.getPageNumber(), pageable.getPageSize());

        assertNotNull(result);
        assertEquals(5, result.getContent().size());
        assertEquals(new BigDecimal("1000.00"), result.getContent().getFirst().getScore());


        // Verificar que la posición se establece correctamente
        result.getContent().forEach(user -> {
            int posicion = result.getContent().indexOf(user);
            assertEquals(posicion + 1, user.getPosition());
        });
        verify(userRepository, times(1)).findAllUsersWithRank(pageable);
    }

    @Test
    void getUsersOrderedByScoreFromUser_ShouldReturnCorrectPage() {
        Long userId = 4L;
        Pageable pageable = PageRequest.of(0, 5); // Base Pageable of 5 elements per page

        int userPosition = findUserPosition(userId);
        int pageNumber = userPosition / pageable.getPageSize();

        testUserRankingScenario(userId, userPosition, pageable);

        verify(userRepository, times(2)).findById(userId); // Once for the target user itself, once for projection conversion
        verify(userRepository, times(1)).findUserRankPosition(userId);

        Pageable expectedPageable = PageRequest.of(pageNumber, pageable.getPageSize());
        verify(userRepository, times(1)).findAllUsersWithRank(expectedPageable);
    }

    @Test
    void getUsersOrderedByScoreFromUser_UserInFirstPage_ShouldReturnFirstPage() {
        Long userId = 10L;
        Pageable pageable = PageRequest.of(0, 5); // Un Pageable base de 5 elementos por página

        int userPosition = findUserPosition(userId); // Obtiene la posición (índice 0-basado)
        int pageNumber = userPosition / pageable.getPageSize();

        assertEquals(0, userPosition);
        assertEquals(0, pageNumber);

        testUserRankingScenario(userId, userPosition, pageable);

        verify(userRepository, times(2)).findById(userId); // Una vez para el usuario objetivo, otra para la conversión de proyección
        verify(userRepository, times(1)).findUserRankPosition(userId);
        verify(userRepository, times(1)).findAllUsersWithRank(PageRequest.of(0, 5));
    }

    @Test
    void getUsersOrderedByScoreFromUser_UserInLastPage_ShouldReturnLastPage() {
        Long userId = 11L;
        Pageable pageable = PageRequest.of(0, 2);

        int userPosition = findUserPosition(userId);
        assertEquals(10, userPosition);

        testUserRankingScenario(userId, userPosition, pageable);

        verify(userRepository, times(2)).findById(userId);
        verify(userRepository, times(1)).findUserRankPosition(userId);
        verify(userRepository, times(1)).findAllUsersWithRank(any(Pageable.class));
    }

    @Test
    void getUsersOrderedByScoreFromUser_UserDoesNotExist_ShouldThrowNotFoundException() {
        // Arrange
        Long userId = 999L;
        Pageable pageable = PageRequest.of(0, 5);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        // Act & Assert
        assertThrows(NotFoundException.class, () -> userService.getUsersOrderedByScoreFromUser(userId, pageable.getPageNumber(),pageable.getPageSize()));
        // Verify
        verify(userRepository, times(1)).findById(userId);
        verify(userRepository, never()).findUserRankPosition(any());
        verify(userRepository, never()).findAllUsersWithRank(any(Pageable.class));
    }

    @Test
    void getUsersOrderedByScoreFromUser_UsersWithSameScore_ShouldUseDifferentPositions() {
        Long userId2 = 2L;   // Score: 200
        Long userId11 = 11L; // Score: 200
        Pageable pageable = PageRequest.of(0, 3); // Base pageable for tests

        int position2 = findUserPosition(userId2);
        int position11 = findUserPosition(userId11);

        List<User> usersWithSameScore = getSortedUsers().stream()
                .filter(u -> u.getId().equals(userId2) || u.getId().equals(userId11))
                .toList();

        User user2 = usersWithSameScore.stream().filter(u -> u.getId().equals(userId2)).findFirst().orElseThrow();
        User user11 = usersWithSameScore.stream().filter(u -> u.getId().equals(userId11)).findFirst().orElseThrow();

        assertNotEquals(position2, position11);
        assertEquals(user2.getScore(), user11.getScore());
        assertTrue(position2 < position11);

        testUserRankingScenario(userId2, position2, pageable);
        testUserRankingScenario(userId11, position11, pageable);
    }

    @Test
    void getUsersOrderedByScoreFromUser_MultipleUsersWithSameScore_ShouldBeOrderedById() {
        Long userId3 = 3L;  // Score: 300
        Long userId12 = 12L; // Score: 300
        Pageable pageable = PageRequest.of(0, 3); // Base pageable for tests

        User user3 = getSortedUsers().stream().filter(u -> u.getId().equals(userId3)).findFirst().orElseThrow();
        User user12 = getSortedUsers().stream().filter(u -> u.getId().equals(userId12)).findFirst().orElseThrow();

        assertEquals(user3.getScore(), user12.getScore());
        assertTrue(user3.getId() < user12.getId());

        int position3 = findUserPosition(userId3);
        int position12 = findUserPosition(userId12);

        assertTrue(position3 < position12);
        getSortedUsers().forEach(user ->
                when(userRepository.findById(user.getId())).thenReturn(Optional.of(user))
        );

        testUserRankingScenario(userId3, position3, pageable);
        testUserRankingScenario(userId12, position12, pageable);
    }

    private void testUserRankingScenario(Long userId, int userPosition, Pageable basePageable) {

        when(userRepository.findUserRankPosition(userId)).thenReturn(userPosition);

        int pageNumber = userPosition / basePageable.getPageSize();
        Pageable expectedPageable = PageRequest.of(pageNumber, basePageable.getPageSize());


        List<User> sortedUsers = getSortedUsers();
        int startIdx = pageNumber * basePageable.getPageSize();
        int endIdx = Math.min(startIdx + basePageable.getPageSize(), sortedUsers.size());

        List<UserRankingProjection> projections = IntStream.range(startIdx, endIdx)
                .mapToObj(i -> {
                    User u = sortedUsers.get(i);
                    return (UserRankingProjection) new UserRankingProjectionImpl(u.getId(), u.getUsername(), u.getScore(), i + 1);
                })
                .toList();

        Page<UserRankingProjection> projectionPage = new PageImpl<>(projections, expectedPageable, sortedUsers.size());

        when(userRepository.findAllUsersWithRank(any())).thenReturn(projectionPage);

        Page<User> result = userService.getUsersOrderedByScoreFromUser(userId, projectionPage.getNumber(), projectionPage.getSize());

        assertNotNull(result);
        assertEquals(pageNumber, result.getNumber());
        assertEquals(projectionPage.getTotalElements(), result.getTotalElements()); // Asegurar que el total de elementos coincide
        assertEquals(projectionPage.getSize(), result.getContent().size()); // Asegurar que el tamaño de la página coincide

        // Verificar el contenido de la página usando lambdas y el campo 'position'
        result.getContent().forEach(actualUser -> {
            int position = result.getContent().indexOf(actualUser);
            User expectedUser = sortedUsers.get(startIdx + position);
            assertEquals(expectedUser.getId(), actualUser.getId());
            assertEquals(startIdx + position + 1, actualUser.getPosition());
            assertEquals(expectedUser.getScore(), actualUser.getScore());
        });
    }


    @Test
    @DisplayName("Estadisticas usuario: contesto bien 4 de 5 preguntas")
    void getUserStatistics_4of5Answers() {

        when(answerDao.countByUserId(anyLong())).thenReturn(5);
        when(answerDao.countByUserIdAndErrorReasonIsNull(anyLong())).thenReturn(4);

        StatsResponse expectedStatsResponse = new StatsResponse(1, 4, 5);
        StatsResponse statsResponse = userService.getStatisticsFromUser(anyLong());

        assertEquals(expectedStatsResponse, statsResponse);
    }

    @Test
    @DisplayName("Crear usuario con username existente")
    void createUser_UsernameExists() {

        User user = User.builder()
                .username("username")
                .email("email")
                .password("password")
                .build();

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));
        assertThrows(ConflictException.class, () -> userService.validateUsernameEmail(user));
    }

    @Test
    @DisplayName("Login con credenciales válidas devuelve el token JWT")
    void login_WithValidCredentials_ShouldReturnToken() {
        // Arrange
        String rawPassword = "Pelele";
        String encodedPassword = "$2a$10$hashedPassword";
        User user = User.builder()
                .id(1L)
                .username("juanceto01")
                .password(encodedPassword)
                .build();

        when(userRepository.findByUsername("juanceto01")).thenReturn(Optional.of(user));
        when(encoder.matches(rawPassword, encodedPassword)).thenReturn(true);
        when(jwtUtils.generateToken(anyMap(), eq("juanceto01"))).thenReturn("jwt-token");

        // Act
        LoginResponseDTO response = userService.login(LoginRequestDTO.builder()
                .username("juanceto01")
                .password(rawPassword)
                .build());

        // Assert
        assertNotNull(response);
        assertEquals("jwt-token", response.token());
        verify(jwtUtils, times(1)).generateToken(anyMap(), eq("juanceto01"));
    }

    @Test
    @DisplayName("Login con contraseña incorrecta lanza BadCredentialsException y no genera token")
    void login_WithWrongPassword_ShouldThrowBadCredentials() {
        // Arrange
        String encodedPassword = "$2a$10$hashedPassword";
        User user = User.builder()
                .id(1L)
                .username("juanceto01")
                .password(encodedPassword)
                .build();

        when(userRepository.findByUsername("juanceto01")).thenReturn(Optional.of(user));
        when(encoder.matches("contraseñaIncorrecta", encodedPassword)).thenReturn(false);

        // Act & Assert
        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> userService.login(LoginRequestDTO.builder()
                        .username("juanceto01")
                        .password("contraseñaIncorrecta")
                        .build()));

        assertEquals(Exceptions.INVALID_CREDENTIALS, exception.getMessage());
        verify(jwtUtils, never()).generateToken(anyMap(), anyString());
    }

    @Test
    @DisplayName("Login con usuario inexistente lanza BadCredentialsException y no genera token")
    void login_WithUnknownUsername_ShouldThrowBadCredentials() {
        // Arrange
        when(userRepository.findByUsername("noExiste")).thenReturn(Optional.empty());

        // Act & Assert
        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> userService.login(LoginRequestDTO.builder()
                        .username("noExiste")
                        .password("Pelele")
                        .build()));

        assertEquals(Exceptions.INVALID_CREDENTIALS, exception.getMessage());
        verifyNoInteractions(encoder, jwtUtils);
    }

    @Test
    @DisplayName("Login de usuario sin contraseña almacenada lanza BadCredentialsException y no genera token")
    void login_WithNullStoredPassword_ShouldThrowBadCredentials() {
        // Arrange
        User user = User.builder()
                .id(2L)
                .username("google_user")
                .password(null)
                .build();

        when(userRepository.findByUsername("google_user")).thenReturn(Optional.of(user));

        // Act & Assert
        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> userService.login(LoginRequestDTO.builder()
                        .username("google_user")
                        .password("Pelele")
                        .build()));

        assertEquals(Exceptions.INVALID_CREDENTIALS, exception.getMessage());
        verify(jwtUtils, never()).generateToken(anyMap(), anyString());
    }

    @Test
    @DisplayName("Login sin contraseña en la request lanza BadCredentialsException sin validar contra el repositorio")
    void login_WithNullRawPassword_ShouldThrowBadCredentials() {
        // Arrange
        User user = User.builder()
                .id(1L)
                .username("juanceto01")
                .password("$2a$10$hashedPassword")
                .build();

        when(userRepository.findByUsername("juanceto01")).thenReturn(Optional.of(user));

        // Act & Assert
        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> userService.login(LoginRequestDTO.builder()
                        .username("juanceto01")
                        .password(null)
                        .build()));

        assertEquals(Exceptions.INVALID_CREDENTIALS, exception.getMessage());
        verifyNoInteractions(encoder, jwtUtils);
    }

    @AllArgsConstructor
    private static class UserRankingProjectionImpl implements UserRankingProjection {
        private Long id;
        private String userName;
        private BigDecimal score;
        private Integer position;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getUserName() {
            return userName;
        }

        @Override
        public BigDecimal getScore() {
            return score;
        }

        @Override
        public Integer getPosition() {
            return position;
        }
    }
}