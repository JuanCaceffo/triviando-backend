package com.example.proyecto2025_BE.service;


import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.ConflictException;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.exceptions.ValidationException;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.Ranking;
import com.example.proyecto2025_BE.model.dto.StatsResponse;
import com.example.proyecto2025_BE.model.dto.login.LoginRequestDTO;
import com.example.proyecto2025_BE.model.dto.login.LoginResponseDTO;
import com.example.proyecto2025_BE.repository.ResponseRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.security.JwtUtil;
import com.example.proyecto2025_BE.utils.JsonViewPage;
import com.example.proyecto2025_BE.utils.UserRankingProjection;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;


@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ResponseRepository responseRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtils;

    public void validateUsernameEmail(User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw ConflictException.build("El email '" + user.getEmail() + "' ya está en uso.");
        }

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw ConflictException.build("El nombre de usuario '" + user.getUsername() + "' ya está en uso.");
        }
    }

    @Transactional(readOnly = true)
    public User retrieve(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> NotFoundException.build(Exceptions.NOT_FOUND));
    }

    public LoginResponseDTO update(User updatedUser) {
        User existingUser = ValidarUsuario(updatedUser);

        Optional.ofNullable(updatedUser.getName()).ifPresent(existingUser::setName);
        Optional.ofNullable(updatedUser.getLastName()).ifPresent(existingUser::setLastName);
        Optional.ofNullable(updatedUser.getPhoneNumber()).ifPresent(existingUser::setPhoneNumber);
        Optional.ofNullable(updatedUser.getCountryCode()).ifPresent(existingUser::setCountryCode);
        Optional.ofNullable(updatedUser.getJoinDate()).ifPresent(existingUser::setJoinDate);
        Optional.ofNullable(updatedUser.getBirthDate()).ifPresent(existingUser::setBirthDate);
        User savedUser = userRepository.save(existingUser);

        String token = jwtUtils.generateToken(Map.of(
                "id", savedUser.getId(),
                "fullname", savedUser.getFullName(),
                "account", savedUser.getAccount()
            ),
            savedUser.getUsername()
        );

        return LoginResponseDTO.builder().token(token).build();
    }

    private User ValidarUsuario(User updatedUser) {
        Long userId = updatedUser.getId();
        if (userId == null) {
            throw new ValidationException("El ID del usuario es requerido para la actualización.");
        }

        User existingUser = this.retrieve(userId);

        validarCampoUnico(
                updatedUser.getUsername(),
                existingUser.getUsername(),
                "nombre de usuario",
                userRepository::findByUsername,
                existingUser::setUsername);

        validarCampoUnico(
                updatedUser.getEmail(),
                existingUser.getEmail(),
                "email",
                userRepository::findByEmail,
                existingUser::setEmail);

        return existingUser;
    }

    private <T> void validarCampoUnico(T newValue, T existingValue, String fieldName, Function<T, Optional<User>> findByMethod,
                                       Consumer<T> setterMethod) {
        Optional.ofNullable(newValue)
                .filter(value -> !String.valueOf(value).isBlank() && !value.equals(existingValue))
                .ifPresent(value -> {
                    findByMethod.apply(value).ifPresent(user -> {
                        throw new ValidationException("El " + fieldName + " '" + value + "' ya está en uso.");
                    });
                    setterMethod.accept(value);
                });
    }

    public void delete(Long id) {
        User user = retrieve(id);
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public User findByUsername(String userName) {
        return userRepository.findByUsername(userName)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + userName));
    }

    public Page<User> getUsersOrderedByScoreDesc(int page, int size) {
        Pageable pageable = createPageable(page, size);
        Page<UserRankingProjection> projectionPage = userRepository.findAllUsersWithRank(pageable);
        List<User> users = projectionPage.getContent().stream()
                .map(this::convertProjectionToUser)
                .toList();
        return new JsonViewPage<>(users, projectionPage.getPageable(), projectionPage.getTotalElements());
    }

    public Page<User> getUsersOrderedByScoreFromUser(Long userId, int page, int size) {
        this.retrieve(userId);
        Integer userPosition = userRepository.findUserRankPosition(userId);
        if (userPosition == null || userPosition <= 0) {
            return getUsersOrderedByScoreDesc(page, size);
        }
        int zeroBasedPosition = userPosition - 1;
        int pageNumber = zeroBasedPosition / size;//3
        return getUsersOrderedByScoreDesc(pageNumber, size);
    }

    @Transactional(readOnly = true)
    public Page<Ranking> getWeeklyRanking(int page, int size) {
        Pageable pageable = createPageable(page, size);
        Page<UserRankingProjection> projectionPage = userRepository.findWeeklyRanking(pageable);
        List<Ranking> users = projectionPage.getContent().stream()
                .map(this::convertProjectionToRankin)
                .toList();
        return new PageImpl<>(users, pageable, projectionPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<Ranking> getWeeklyRanking(Long userId, int page, int size) {
        this.retrieve(userId);
        Integer userPosition = userRepository.findUserRankWeeklyPosition(userId);
        if (userPosition == null || userPosition <= 0) {
            return getWeeklyRanking(page, size);
        }
        int zeroBasedPosition = userPosition - 1;
        int pageNumber = zeroBasedPosition / size;
        return getWeeklyRanking(pageNumber, size);
    }

    private User convertProjectionToUser(UserRankingProjection projection) {

        User user = userRepository.findById(projection.getId())
                .orElseGet(User::new); // Fallback a un nuevo User si por alguna razón no existe
        user.setPosition(projection.getPosition());
        return user;
    }

    private Ranking convertProjectionToRankin(UserRankingProjection projection) {
        return new Ranking(projection.getId(), projection.getUserName(), projection.getScore().doubleValue(), projection.getPosition());
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(page, size);
    }

    public StatsResponse getStatisticsFromUser(Long userId) {
        var totalQuestions = responseRepository.countByUserId(userId);
        var correctAnswers = responseRepository.countByUserIdAndErrorReasonIsNull(userId);
        var totalQuizzes = totalQuestions / 5;
        return new StatsResponse(totalQuizzes, correctAnswers, totalQuestions);
    }

    public void markUserAsSubscribed(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> NotFoundException.build("Usuario no encontrado al intentar suscribir plan premium"
                        + " para email: " + email));
        user.setAccount(Account.PREMIUM);
        userRepository.save(user);
    }

    @Transactional
    public LoginResponseDTO create(User user) {
        validateUsernameEmail(user);
        var encodedPass = encoder.encode(user.getPassword());
        user.setPassword(encodedPass);

        var userCreated = userRepository.save(user);

        var token = jwtUtils.generateToken(Map.of(
                        "id", userCreated.getId(),
                        "fullname", userCreated.getFullName(),
                        "account", userCreated.getAccount()
                ),
                userCreated.getUsername()
        );

        return LoginResponseDTO.builder().token(token).build();
    }

    @Transactional
    public LoginResponseDTO login(@Valid LoginRequestDTO user) {

        User fetchedUser = findByUsername(user.username());

        String token = jwtUtils.generateToken(Map.of(
                        "id", fetchedUser.getId(),
                        "fullname", fetchedUser.getFullName(),
                        "account", fetchedUser.getAccount()
                ),
                fetchedUser.getUsername()
        );

        return LoginResponseDTO.builder().token(token).build();
    }
}