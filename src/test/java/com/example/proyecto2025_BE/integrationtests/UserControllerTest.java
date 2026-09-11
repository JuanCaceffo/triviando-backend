package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.model.Answer;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.model.dto.StatsResponse;
import com.example.proyecto2025_BE.repository.ResponseRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("User Controller Tests")
public class UserControllerTest {

    private static final Long INEXISTENT_USER_ID = 99L;
    private static Long EXISTENT_USER_ID;
    private static String jwtToken;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository dao;
    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private ResponseRepository responseRepository;

    @BeforeEach
    void beforeEach() {
        // Limpiamos la base de datos
        dao.deleteAll();
        dao.flush();  // Forzamos la sincronización con la base de datos

        jwtToken = crearUser(
                User.builder()
                        .username("juanceto01")
                        .name("Pepe")
                        .lastName("Palala")
                        .email("dsadsa@dsada.com")
                        .build(),
                "Pelele"
        );
        EXISTENT_USER_ID = dao.findByUsername("juanceto01").get().getId();

        // Configuramos el mapper
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }


    @Test
    @DisplayName("Cuando busco un user por id, y este existe, obtengo dicho recurso")
    void retrieveTest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/users/{id}", EXISTENT_USER_ID).header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.name").value("Pepe"))
                .andExpect(jsonPath("$.lastName").value("Palala"));
    }

    @Test
    @DisplayName("Cuando busco un user por id y este no es encontrado el sistema devuelve Not Found")
    void findByIdNotFoundTest() throws Exception {
        mockMvc
                .perform(MockMvcRequestBuilders.get("/users/{id}", INEXISTENT_USER_ID).header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Se actualiza un user de manera exitosa")
    @DirtiesContext
    void updateTest() throws Exception {
        User user = User.builder()
                .name("Pepe")
                .lastName("Palala")
                .username("pepe.palala")
                .build();

        user = dao.save(user);

        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId().toString());
        map.put("name", "Emiliano");
        map.put("lastName", "Emil");
        map.put("phoneNumber", "1234567890");

        String requestBody = mapper.writeValueAsString(map);

        mockMvc
                .perform(MockMvcRequestBuilders.put("/users")
                        .contentType("application/json")
                        .content(requestBody)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        User updatedUser = dao.findById(user.getId()).orElseThrow();
        assertEquals("Emiliano", updatedUser.getName());
        assertEquals("Emil", updatedUser.getLastName());
        assertEquals("1234567890", updatedUser.getPhoneNumber());
    }

    @Test
    @DisplayName("Se crea un user de manera exitosa")
    void createTest() throws Exception {
        User userRequestDTO = User.builder()
                .username("PepePalala")
                .email("pepe.palala@gmail.com")
                .password("123456")
                .joinDate(LocalDateTime.now())
                .build();

        String requestBody = mapper.writeValueAsString(userRequestDTO);

        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(content().contentType("application/json"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Error al crear un usuario con email vacío")
    void createUserWithEmptyEmailTest() throws Exception {
        User invalidUser = User.builder()
                .username("Test")
                .email("")
                .password("password123")
                .joinDate(LocalDateTime.now())
                .build();

        String requestBody = mapper.writeValueAsString(invalidUser);

        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @DisplayName("Error al crear un usuario con email inválido")
    void createUserWithInvalidEmailFormatTest() throws Exception {
        User invalidUser = User.builder()
                .name("Test")
                .lastName("User")
                .email("invalid-email")
                .password("password123")
                .joinDate(LocalDateTime.now())
                .build();

        String requestBody = mapper.writeValueAsString(invalidUser);

        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @DisplayName("Error al crear un usuario con nombre vacío")
    void createUserWithEmptyFullNameTest() throws Exception {
        User invalidUser = User.builder()
                .name("")
                .lastName("")
                .email("test@example.com")
                .password("password123")
                .joinDate(LocalDateTime.now())
                .build();

        String requestBody = mapper.writeValueAsString(invalidUser);

        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @DisplayName("Error al crear un usuario con contraseña vacía")
    void createUserWithEmptyPasswordTest() throws Exception {
        User invalidUser = User.builder()
                .name("Test")
                .lastName("User")
                .email("test@example.com")
                .password("")
                .joinDate(LocalDateTime.now())
                .build();

        String requestBody = mapper.writeValueAsString(invalidUser);

        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @DisplayName("Error al crear un usuario con campos nulos")
    void createUserWithNullFieldsTest() throws Exception {
        User invalidUser = User.builder()
                .joinDate(LocalDateTime.now())
                .build();

        String requestBody = mapper.writeValueAsString(invalidUser);

        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").exists());
    }

    @Test
    @DisplayName("Error al crear un usuario que ya existe")
    void createDuplicateUserTest() throws Exception {

        User user = User.builder()
                .name("Existing")
                .lastName("User")
                .email("existing@example.com")
                .password("password123")
                .joinDate(LocalDateTime.now())
                .build();

        dao.save(user);

        User duplicateUser = User.builder()
                .name("Another")
                .lastName("User")
                .email("existing@example.com")
                .password("differentpassword")
                .joinDate(LocalDateTime.now())
                .build();

        String requestBody = mapper.writeValueAsString(duplicateUser);

        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").exists());
    }


    @Test
    @DisplayName("Estadisticas de un user que ha respondido preguntas")
    void estadisticasTest() throws Exception {
        when(responseRepository.countByUserId(EXISTENT_USER_ID)).thenReturn(50);
        when(responseRepository.countByUserIdAndErrorReasonIsNull(EXISTENT_USER_ID)).thenReturn(35);
        StatsResponse response = new StatsResponse(10, 35, 50);

        mockMvc.perform(MockMvcRequestBuilders.get("/users/statistics/{id}", EXISTENT_USER_ID).header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json(mapper.writeValueAsString(response)));
    }

    @Test
    @DisplayName("Estadisticas de un user que no ha respondido preguntas")
    void estadisticasInexistentUserTest() throws Exception {
        when(responseRepository.countByUserId(EXISTENT_USER_ID)).thenReturn(0);
        when(responseRepository.countByUserIdAndErrorReasonIsNull(EXISTENT_USER_ID)).thenReturn(0);
        StatsResponse response = new StatsResponse(0, 0, 0);

        mockMvc.perform(MockMvcRequestBuilders.get("/users/statistics/{id}", EXISTENT_USER_ID).header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json(mapper.writeValueAsString(response)));
    }

    @Test
    @DisplayName("Obtener el ranking de usuarios ordenados por score")
    void getUsersOrderedByScoreTest() throws Exception {
        // Preparar datos de prueba
        User user1 = User.builder().username("Usuario Alto Score").build();
        User user2 = User.builder().username("Usuario Medio Score").build();
        User user3 = User.builder().username("Usuario Bajo Score").build();

        Answer a1 = Answer.builder().score(new BigDecimal(100)).build();
        Answer a2 = Answer.builder().score(new BigDecimal(50)).build();
        Answer a3 = Answer.builder().score(new BigDecimal(25)).build();

        user1.setAnswers(List.of(a1));
        user2.setAnswers(List.of(a2));
        user3.setAnswers(List.of(a3));

        dao.save(user1);
        dao.save(user2);
        dao.save(user3);

        // Ejecutar y verificar
        mockMvc.perform(MockMvcRequestBuilders.get("/users/ranking").header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.content[0].username").value("Usuario Alto Score"))
                .andExpect(jsonPath("$.content[0].score", closeTo(100.0, 0)))
                .andExpect(jsonPath("$.content[1].username").value("Usuario Medio Score"))
                .andExpect(jsonPath("$.content[1].score", closeTo(50.0, 0)))
                .andExpect(jsonPath("$.content[2].username").value("Usuario Bajo Score"))
                .andExpect(jsonPath("$.content[2].score", closeTo(25.0, 0)));
    }

    @Test
    @DisplayName("Obtener el ranking desde un usuario específico")
    void getUsersOrderedByScoreFromUserTest() throws Exception {
        User userHigh = User.builder().username("Usuario Alto").build();
        User userMid = User.builder().username("Usuario Medio").build();
        User userLow = User.builder().username("Usuario Bajo").build();

        Answer a1 = Answer.builder().score(new BigDecimal(500)).build();
        Answer a2 = Answer.builder().score(new BigDecimal(300)).build();
        Answer a3 = Answer.builder().score(new BigDecimal(100)).build();

        userHigh.setAnswers(List.of(a1));
        userMid.setAnswers(List.of(a2));
        userLow.setAnswers(List.of(a3));

        userHigh = dao.save(userHigh);
        userMid = dao.save(userMid);
        userLow = dao.save(userLow);

        Long midUserId = userMid.getId();

        mockMvc.perform(MockMvcRequestBuilders.get("/users/ranking/{userId}", midUserId)
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.content", hasSize(greaterThan(0))))
                // Verificar que los scores están ordenados correctamente
                .andExpect(jsonPath("$.content[0].score", closeTo(500.0, 0)))
                .andExpect(jsonPath("$.content[1].score", closeTo(300.0, 0)))
                .andExpect(jsonPath("$.content[2].score", closeTo(100.0, 0)))
                // Verificar que el usuario medio está en la posición correcta
                .andExpect(jsonPath("$.content[1].id").value(midUserId.intValue()))
                .andExpect(jsonPath("$.number").isNumber());
    }

    @Test
    @DisplayName("Obtener el ranking desde un usuario con mismo score que otro")
    void getUsersOrderedByScoreFromUserWithSameScoreTest() throws Exception {
        // Preparar datos de prueba - Usuarios con mismos scores pero diferentes IDs
        User user1 = User.builder().username("Usuario 1").build();
        User user2 = User.builder().username("Usuario 2").build();
        // Añadir usuarios adicionales para un ranking más completo
        User userHigh = User.builder().username("Usuario Alto").build();
        User userLow = User.builder().username("Usuario Bajo").build();
        Answer a1 = Answer.builder().score(new BigDecimal(300)).build();
        Answer a2 = Answer.builder().score(new BigDecimal(300)).build();
        Answer a3 = Answer.builder().score(new BigDecimal(500)).build();
        Answer a4 = Answer.builder().score(new BigDecimal(100)).build();

        user1.setAnswers(List.of(a1));
        user2.setAnswers(List.of(a2));
        userHigh.setAnswers(List.of(a3));
        userLow.setAnswers(List.of(a4));


        userHigh = dao.save(userHigh);
        user1 = dao.save(user1);
        user2 = dao.save(user2);
        userLow = dao.save(userLow);

        Long user1Id = user1.getId();
        Long user2Id = user2.getId();

        // Verificar que ambos tienen el mismo score
        assertEquals(user1.getScore().compareTo(user2.getScore()), 0);

        // Asumimos que user1 tiene ID menor que user2 por orden de inserción
        assertTrue(user1Id < user2Id, "Para esta prueba, user1 debe tener un ID menor que user2");

        // Prueba para el primer usuario
        mockMvc.perform(MockMvcRequestBuilders.get("/users/ranking/{userId}", user1Id)
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(4))))
                // Verificar que cuando hay mismo score, se ordenan por ID
                .andExpect(jsonPath("$.content[1].id").value(user1Id.intValue()))
                .andExpect(jsonPath("$.content[2].id").value(user2Id.intValue()))
                .andExpect(jsonPath("$.content[1].score", closeTo(300.0, 0)))
                .andExpect(jsonPath("$.content[2].score", closeTo(300.0, 0)))
                .andExpect(jsonPath("$.number").isNumber());

        // Prueba para el segundo usuario
        mockMvc.perform(MockMvcRequestBuilders.get("/users/ranking/{userId}", user2Id)
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.content[*].id", hasItem(user2Id.intValue())))
                // El orden debe ser consistente con la prueba anterior
                .andExpect(jsonPath("$.content[1].id").value(user1Id.intValue()))
                .andExpect(jsonPath("$.content[2].id").value(user2Id.intValue()))
                .andExpect(jsonPath("$.number").isNumber());
    }

    @Test
    @DisplayName("Obtener el ranking desde un usuario inexistente debería retornar error 404")
    void getUsersOrderedByScoreFromInexistentUserTest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/users/ranking/{userId}", INEXISTENT_USER_ID)
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cuando se loguea un usuario, y este esta registrado, obtengo dicho recurso")
    void loginTest() throws Exception {
        User requestBody = User.builder()
                .username("juanceto01")
                .password("Pelele")
                .joinDate(LocalDateTime.now())
                .build();

        String jsonBody = mapper.writeValueAsString(requestBody);

        mockMvc.perform(MockMvcRequestBuilders.post("/users/login")
                        .contentType("application/json")
                        .content(jsonBody))
                .andExpect(content().contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").isString());
    }

    String crearUser(User user, String pass) {
        var encodedPass = encoder.encode(pass);
        user.setPassword(encodedPass);

        dao.save(user);
        return jwtUtil.generateToken(
                Map.of(
                        "id", user.getId(),
                        "fullname", user.getFullName(),
                        "account", user.getAccount()
                ),
                user.getUsername()
        );
    }
}
