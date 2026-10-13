package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.dto.FeedbackDTO;
import com.example.proyecto2025_BE.model.dto.Topics;

import java.time.LocalDateTime;
import java.util.List;

public interface IQuestion {

    Pregunta getPreguntaById(String id);

    void saveAll(List<Pregunta> preguntas);

    List<Topics> contarPreguntasPorTopicoDeUsuario(Long userId);

    boolean existsByTopicAndUser(String topic, Long userId);

    List<Pregunta> findByTopicoAndUserId(String topico, Long userId);

    List<Pregunta> obtenerPreguntasNoRespondidasPorTopico(Long userId, String topico);

    String getTopicFromQuestion(String topic, Long userId);

    void saveFeedback(FeedbackDTO feedbackDTO);

    Boolean canCreateTopic(Long userId, LocalDateTime startOfDay, LocalDateTime endOfDay);


    Boolean canCreateQuestion(Long userId, LocalDateTime startOfDay, LocalDateTime endOfDay);

}
