package com.example.proyecto2025_BE.integrationtests;

import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.model.Account;
import com.example.proyecto2025_BE.model.GeneratedTopic;
import com.example.proyecto2025_BE.model.GenerationJob;
import com.example.proyecto2025_BE.model.JobStatus;
import com.example.proyecto2025_BE.model.User;
import com.example.proyecto2025_BE.repository.GenerationJobRepository;
import com.example.proyecto2025_BE.repository.QuestionRepository;
import com.example.proyecto2025_BE.repository.UserRepository;
import com.example.proyecto2025_BE.security.JwtUtil;
import com.example.proyecto2025_BE.service.DocumentGenerationWorker;
import com.example.proyecto2025_BE.service.document.DocumentTextExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Document Generation Controller Test")
class DocumentGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @MockitoBean
    private GenerationJobRepository jobRepository;
    @MockitoBean
    private DocumentTextExtractionService textExtractionService;
    @MockitoBean
    private DocumentGenerationWorker worker;
    @MockitoBean
    private QuestionRepository questionRepository;
    @MockitoBean
    private UserRepository userRepository;

    private String jwtToken;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .username("juanceto01")
                .password("encoded")
                .account(Account.PREMIUM)
                .build();

        jwtToken = jwtUtil.generateToken(
                Map.of("id", user.getId(), "fullname", "Test User", "account", user.getAccount()),
                user.getUsername());

        when(userRepository.findByUsername("juanceto01")).thenReturn(Optional.of(user));
        when(jobRepository.findFirstByUserIdAndStatusIn(anyLong(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("POST multipart encola la generación y devuelve 202 con Location")
    void encolaGeneracion() throws Exception {
        when(textExtractionService.extract(any(MultipartFile.class))).thenReturn("texto extraído");
        when(jobRepository.save(any(GenerationJob.class))).thenAnswer(invocation -> {
            GenerationJob job = invocation.getArgument(0);
            job.setId("job1");
            return job;
        });

        mockMvc.perform(multipart("/preguntas/document/generate")
                        .file(archivoTxt())
                        .param("topic", "Biología")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/preguntas/document/jobs/job1")))
                .andExpect(jsonPath("$.jobId").value("job1"))
                .andExpect(jsonPath("$.topic").value("Biología"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(worker).process("job1");
    }

    @Test
    @DisplayName("POST con usuario FREE devuelve 403")
    void usuarioFreeDevuelve403() throws Exception {
        User freeUser = User.builder()
                .id(2L)
                .username("freeuser")
                .password("encoded")
                .account(Account.FREE)
                .build();
        String freeToken = jwtUtil.generateToken(
                Map.of("id", freeUser.getId(), "fullname", "Free User", "account", freeUser.getAccount()),
                freeUser.getUsername());
        when(userRepository.findByUsername("freeuser")).thenReturn(Optional.of(freeUser));

        mockMvc.perform(multipart("/preguntas/document/generate")
                        .file(archivoTxt())
                        .param("topic", "Biología")
                        .header("Authorization", "Bearer " + freeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Exceptions.PREMIUM_ONLY_DOCUMENT_GENERATION));
    }

    @Test
    @DisplayName("POST sin token devuelve 401")
    void sinTokenDevuelve401() throws Exception {        mockMvc.perform(multipart("/preguntas/document/generate")
                        .file(archivoTxt())
                        .param("topic", "Biología"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST sin archivo devuelve 400")
    void sinArchivoDevuelve400() throws Exception {
        mockMvc.perform(multipart("/preguntas/document/generate")
                        .param("topic", "Biología")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST con tópico vacío devuelve 400")
    void topicoVacioDevuelve400() throws Exception {
        mockMvc.perform(multipart("/preguntas/document/generate")
                        .file(archivoTxt())
                        .param("topic", "   ")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El nombre del tópico es obligatorio"));
    }

    @Test
    @DisplayName("POST con tópico existente devuelve 409")
    void topicoExistenteDevuelve409() throws Exception {
        when(questionRepository.existsByTopicoAndUserId("Biología", 1L)).thenReturn(true);

        mockMvc.perform(multipart("/preguntas/document/generate")
                        .file(archivoTxt())
                        .param("topic", "Biología")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET devuelve el estado y el resultado del job")
    void consultaJob() throws Exception {
        GenerationJob job = GenerationJob.builder()
                .id("job1")
                .userId(1L)
                .topic("Biología")
                .status(JobStatus.COMPLETED)
                .progress(100)
                .result(List.of(GeneratedTopic.builder().topic("Biología").size(10).emoji("🧬").build()))
                .updatedAt(LocalDateTime.now())
                .build();
        when(jobRepository.findByIdAndUserId("job1", 1L)).thenReturn(Optional.of(job));

        mockMvc.perform(get("/preguntas/document/jobs/{jobId}", "job1")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.result[0].topic").value("Biología"))
                .andExpect(jsonPath("$.result[0].size").value(10))
                .andExpect(jsonPath("$.result[0].emoji").value("🧬"));
    }

    @Test
    @DisplayName("GET de un job inexistente o ajeno devuelve 404")
    void consultaJobInexistente() throws Exception {
        when(jobRepository.findByIdAndUserId(eq("job1"), eq(1L))).thenReturn(Optional.empty());

        mockMvc.perform(get("/preguntas/document/jobs/{jobId}", "job1")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    private MockMultipartFile archivoTxt() {
        return new MockMultipartFile("file", "apunte.txt", "text/plain",
                "La célula es la unidad estructural y funcional de los seres vivos.".getBytes());
    }
}
