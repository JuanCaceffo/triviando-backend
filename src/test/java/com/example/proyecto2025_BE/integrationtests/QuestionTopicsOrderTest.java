package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.Answer;
import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.Topic;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.repository.QuestionRepository;
import com.example.proyecto2025_BE.repository.ResponseRepository;
import com.example.proyecto2025_BE.repository.TopicRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica el orden y el conteo de tópicos por usuario contra una base real (H2),
 * porque ese comportamiento vive en la query de {@link QuestionRepository}.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:topictest_order;DB_CLOSE_DELAY=-1")
@DisplayName("Tópicos por usuario - orden y conteo")
class QuestionTopicsOrderTest {

    @Autowired
    private QuestionService questionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private ResponseRepository responseRepository;

    private User user;

    @BeforeEach
    void setUp() {
        responseRepository.deleteAll();
        questionRepository.deleteAll();
        topicRepository.deleteAll();
        userRepository.deleteAll();

        user = saveUser("topics_order");
    }

    @Test
    @DisplayName("Ordena los tópicos del más reciente al más viejo")
    void ordersTopicsFromNewestToOldest() {
        Topic viejo = topic("Viejo", LocalDateTime.of(2024, 1, 1, 10, 0));
        Topic medio = topic("Medio", LocalDateTime.of(2024, 2, 1, 10, 0));
        Topic nuevo = topic("Nuevo", LocalDateTime.of(2024, 3, 1, 10, 0));
        saveQuestions(viejo, 2);
        saveQuestions(medio, 1);
        saveQuestions(nuevo, 3);

        List<Topics> result = counts();

        assertEquals(List.of("Nuevo", "Medio", "Viejo"), names(result));
        assertEquals(List.of(3, 1, 2), sizes(result));
    }

    @Test
    @DisplayName("Incluye tópicos sin preguntas y con todas las preguntas respondidas")
    void keepsTopicsWithoutPendingQuestions() {
        Topic conPendientes = topic("Con pendientes", LocalDateTime.of(2024, 3, 1, 10, 0));
        Topic respondido = topic("Respondido", LocalDateTime.of(2024, 2, 1, 10, 0));
        Topic sinPreguntas = topic("Sin preguntas", LocalDateTime.of(2024, 1, 1, 10, 0));
        saveQuestions(conPendientes, 2);
        Pregunta pregunta = saveQuestions(respondido, 1).get(0);
        responseRepository.save(Answer.builder()
                .questionId(pregunta.getId())
                .user(user)
                .build());

        List<Topics> result = counts();

        assertEquals(List.of("Con pendientes", "Respondido", "Sin preguntas"), names(result));
        assertEquals(List.of(2, 0, 0), sizes(result));
    }

    @Test
    @DisplayName("Desempata por id cuando la fecha de creación es la misma")
    void tieBreaksByIdWhenCreatedAtIsTheSame() {
        LocalDateTime mismoMomento = LocalDateTime.of(2024, 5, 1, 12, 0);
        Topic primero = topic("Primero", mismoMomento);
        Topic segundo = topic("Segundo", mismoMomento);
        saveQuestions(primero, 1);
        saveQuestions(segundo, 1);

        assertEquals(List.of("Segundo", "Primero"), names(counts()));
    }

    @Test
    @DisplayName("No incluye tópicos de otros usuarios")
    void excludesOtherUsersTopics() {
        User otro = saveUser("topics_order_other");
        topicFor(otro, "Ajeno", LocalDateTime.of(2024, 4, 1, 10, 0));
        topic("Propio", LocalDateTime.of(2024, 1, 1, 10, 0));

        assertEquals(List.of("Propio"), names(counts()));
    }

    private List<Topics> counts() {
        return questionService.contarPreguntasPorTopicoDeUsuario(user.getId());
    }

    private List<String> names(List<Topics> topics) {
        return topics.stream().map(Topics::topic).toList();
    }

    private List<Integer> sizes(List<Topics> topics) {
        return topics.stream().map(topic -> topic.size().intValue()).toList();
    }

    private User saveUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .password("encoded")
                .name("Topics")
                .lastName("Order")
                .account(Account.FREE)
                .joinDate(LocalDateTime.now().minusMonths(1))
                .build());
    }

    private Topic topic(String name, LocalDateTime createdAt) {
        return topicFor(user, name, createdAt);
    }

    private Topic topicFor(User owner, String name, LocalDateTime createdAt) {
        return topicRepository.save(Topic.builder()
                .userId(owner.getId())
                .name(name)
                .emoji("📚")
                .createdAt(createdAt)
                .build());
    }

    private List<Pregunta> saveQuestions(Topic topic, int cantidad) {
        return java.util.stream.IntStream.range(0, cantidad)
                .mapToObj(index -> questionRepository.save(Pregunta.builder()
                        .topic(topic)
                        .enunciado("Pregunta " + index + " de " + topic.getName())
                        .difficulty(Difficulty.LOW)
                        .build()))
                .toList();
    }
}
