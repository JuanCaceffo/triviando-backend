package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Difficulty;
import com.example.proyecto2025_BE.model.LetterOption;
import com.example.proyecto2025_BE.model.Option;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.Topic;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.FeedbackDTO;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.repository.FeedbackRepository;
import com.example.proyecto2025_BE.repository.QuestionRepository;
import com.example.proyecto2025_BE.repository.TopicRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.security.JwtUtil;
import com.example.proyecto2025_BE.utils.ContentNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.proyecto2025_BE.unittests.FeedbackServiceTest.createNegativeFeedbackDTO;
import static com.example.proyecto2025_BE.unittests.FeedbackServiceTest.createPositiveFeedbackDTO;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Preguntas Controller Test")
class QuestionControllerTest {

    private static final String EMOJI = "\uD83D\uDCDA";

    private String jwtToken;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder encoder;

    @MockitoBean
    private QuestionRepository questionRepository;
    @MockitoBean
    private TopicRepository topicRepository;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private FeedbackRepository feedbackRepository;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .username("juanceto01")
                .password(encoder.encode("pelele"))
                .email("dsadsa@dsada.com")
                .build();

        jwtToken = jwtUtil.generateToken(
                Map.of("id", user.getId(), "fullname", user.getFullName(), "account", user.getAccount()),
                user.getUsername());

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Get preguntas by topico - topico existente")
    void getPreguntasByTopico_topicoExistente() throws Exception {
        Topic topic = topic(1L, "Java");
        Pregunta pregunta = pregunta(10L, topic, "¿Qué es una clase?");

        when(topicRepository.findByUserIdAndNameNormalized(1L, ContentNormalizer.topicKey(topic.getName())))
                .thenReturn(Optional.of(topic));
        when(questionRepository.findNotAnsweredByTopicIdAndUserId(topic.getId(), 1L))
                .thenReturn(List.of(pregunta));

        mockMvc.perform(MockMvcRequestBuilders.get("/preguntas")
                        .param("userId", "1")
                        .param("topico", topic.getName())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].topico").value("Java"))
                .andExpect(jsonPath("$[0].enunciado").value("¿Qué es una clase?"));
    }

    @Test
    @DisplayName("Get preguntas by topico - user contesta preguntas")
    void getPreguntasByTopico_userContestaPreguntas() throws Exception {
        Topic topic = topic(1L, "Java");
        Pregunta pregunta = pregunta(10L, topic, "¿Qué es una interfaz?");

        when(topicRepository.findByUserIdAndNameNormalized(1L, ContentNormalizer.topicKey(topic.getName())))
                .thenReturn(Optional.of(topic));
        when(questionRepository.findNotAnsweredByTopicIdAndUserId(topic.getId(), 1L))
                .thenReturn(List.of(pregunta));

        mockMvc.perform(MockMvcRequestBuilders.get("/preguntas")
                        .param("topico", topic.getName())
                        .param("userId", "1")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].enunciado").value("¿Qué es una interfaz?"));
    }

    @Test
    @DisplayName("Get preguntas by topico - topico no existente")
    void getPreguntasByTopico_topicoNoExistente() throws Exception {
        when(topicRepository.findByUserIdAndNameNormalized(1L, ContentNormalizer.topicKey("Geografia")))
                .thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/preguntas")
                        .param("topico", "Geografia")
                        .param("userId", "1")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("Get preguntas sin topico devuelve todas las no respondidas")
    void getPreguntasSinTopico() throws Exception {
        Pregunta pregunta = pregunta(10L, topic(1L, "Java"), "¿Qué es un record?");

        when(questionRepository.findNotAnsweredByUserId(1L)).thenReturn(List.of(pregunta));

        mockMvc.perform(MockMvcRequestBuilders.get("/preguntas")
                        .param("userId", "1")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    @DisplayName("Get preguntas by Id - Id existente")
    void getPreguntaById_IDValid() throws Exception {
        Pregunta pregunta = pregunta(10L, topic(1L, "Java"), "¿Qué es una clase?");

        when(questionRepository.findById(10L)).thenReturn(Optional.of(pregunta));

        mockMvc.perform(MockMvcRequestBuilders.get("/preguntas/{id}", 10L)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.topico").value("Java"));
    }

    @Test
    @DisplayName("Get Preguntas by Id - Id no existente")
    void getPreguntaById_IDInValid() throws Exception {
        when(questionRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/preguntas/{id}", 999L)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Get cantidad de preguntas por topico")
    void getCantidadPreguntasPorTopico() throws Exception {
        when(questionRepository.countUnansweredByTopic(1L)).thenReturn(List.of(
                new TopicCountStub("java", "☕", 5),
                new TopicCountStub("python", EMOJI, 10)));

        List<Topics> expected = List.of(
                Topics.builder().topic("java").size(5).emoji("☕").build(),
                Topics.builder().topic("python").size(10).emoji(EMOJI).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/preguntas/topicos/{id}", 1L)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(expected)));
    }

    @Test
    @DisplayName("Crear feedback positivo")
    void crearFeedbackPositivo() throws Exception {
        FeedbackDTO dto = createPositiveFeedbackDTO();

        mockMvc.perform(MockMvcRequestBuilders.post("/preguntas/send-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Crear feedback negativo")
    void crearFeedbackNegativo() throws Exception {
        FeedbackDTO dto = createNegativeFeedbackDTO();

        mockMvc.perform(MockMvcRequestBuilders.post("/preguntas/send-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Usuario carga solo sus topicos")
    void testCargarTopicosPropios() throws Exception {
        Pregunta user1 = pregunta(10L, topic(1L, "topico1"), "Pregunta 1");
        Pregunta user2 = pregunta(20L, topic(2L, "topico2"), "Pregunta 2");

        when(questionRepository.findNotAnsweredByUserId(1L)).thenReturn(List.of(user1));
        when(questionRepository.findNotAnsweredByUserId(2L)).thenReturn(List.of(user2));

        MvcResult resultUser1 = mockMvc.perform(MockMvcRequestBuilders.get("/preguntas")
                        .param("userId", "1")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode bodyUser1 = objectMapper.readTree(resultUser1.getResponse().getContentAsString());
        assertTrue(bodyUser1.isArray());
        assertTrue(bodyUser1.get(0).get("userId").asLong() == 1L);

        MvcResult resultUser2 = mockMvc.perform(MockMvcRequestBuilders.get("/preguntas")
                        .param("userId", "2")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode bodyUser2 = objectMapper.readTree(resultUser2.getResponse().getContentAsString());
        assertTrue(bodyUser2.get(0).get("userId").asLong() == 2L);
    }

    private Topic topic(Long id, String name) {
        return Topic.builder()
                .id(id)
                .userId(id)
                .name(name)
                .nameNormalized(ContentNormalizer.topicKey(name))
                .emoji(EMOJI)
                .build();
    }

    private Pregunta pregunta(Long id, Topic topic, String enunciado) {
        Pregunta pregunta = Pregunta.builder()
                .id(id)
                .topic(topic)
                .enunciado(enunciado)
                .explicacion("explicacion")
                .difficulty(Difficulty.MEDIUM)
                .options(List.of(
                        Option.builder().letter(LetterOption.A).text("A").correct(true).positionOrder(0).build(),
                        Option.builder().letter(LetterOption.B).text("B").correct(false).positionOrder(1).build()))
                .build();
        pregunta.getOptions().forEach(option -> option.setQuestion(pregunta));
        return pregunta;
    }

    private record TopicCountStub(String topic, String emoji, long cantidad)
            implements QuestionRepository.TopicCount {

        @Override
        public String getTopic() {
            return topic;
        }

        @Override
        public String getEmoji() {
            return emoji;
        }

        @Override
        public long getCantidad() {
            return cantidad;
        }
    }
}
