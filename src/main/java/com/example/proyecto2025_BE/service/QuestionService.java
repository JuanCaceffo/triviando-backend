package com.example.proyecto2025_BE.service;

import com.example.proyecto2025_BE.configuration.QuestionProperties;
import com.example.proyecto2025_BE.constants.Exceptions;
import com.example.proyecto2025_BE.exceptions.NotFoundException;
import com.example.proyecto2025_BE.model.Pregunta;
import com.example.proyecto2025_BE.model.Topic;
import com.example.proyecto2025_BE.model.dto.FeedbackDTO;
import com.example.proyecto2025_BE.model.dto.Topics;
import com.example.proyecto2025_BE.repository.QuestionRepository;
import com.example.proyecto2025_BE.repository.TopicRepository;
import com.example.proyecto2025_BE.utils.ContentNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class QuestionService implements IQuestion {

    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final FeedbackService feedbackService;
    private final QuestionProperties questionProperties;

    @Override
    public Pregunta getPreguntaById(Long id) {
        return questionRepository
                .findById(id)
                .orElseThrow(() -> NotFoundException.build("No se encontro la pregunta con id: " + id));
    }

    @Override
    public List<Topics> contarPreguntasPorTopicoDeUsuario(Long userId) {
        return questionRepository.countUnansweredByTopic(userId).stream()
                .map(topic -> Topics.builder()
                        .topic(topic.getTopic())
                        .size(Math.toIntExact(topic.getCantidad()))
                        .emoji(topic.getEmoji())
                        .build())
                .toList();
    }

    @Override
    public void saveAll(List<Pregunta> preguntas) {
        questionRepository.saveAll(preguntas);
    }

    @Override
    public boolean existsByTopicAndUser(String topic, Long userId) {
        return topicRepository.existsByUserIdAndNameNormalized(userId, ContentNormalizer.topicKey(topic));
    }

    @Override
    public List<Pregunta> findByTopicoAndUserId(String topico, Long userId) {
        return topicRepository
                .findByUserIdAndNameNormalized(userId, ContentNormalizer.topicKey(topico))
                .map(topic -> questionRepository.findByTopic_Id(topic.getId()))
                .orElse(List.of());
    }

    @Override
    public List<Pregunta> obtenerPreguntasNoRespondidasPorTopico(Long userId, String topico) {
        List<Pregunta> candidatas = topico == null || topico.isBlank()
                ? questionRepository.findNotAnsweredByUserId(userId)
                : topicRepository
                        .findByUserIdAndNameNormalized(userId, ContentNormalizer.topicKey(topico))
                        .map(topic -> questionRepository.findNotAnsweredByTopicIdAndUserId(topic.getId(), userId))
                        .orElse(List.of());

        if (candidatas.isEmpty()) {
            return List.of();
        }

        var cantidadPreguntas = questionProperties.getQuestionsPerTopic();
        return new Random().ints(0, candidatas.size())
                .distinct()
                .limit(Math.min(cantidadPreguntas, candidatas.size()))
                .mapToObj(candidatas::get)
                .toList();
    }

    @Override
    public String getTopicFromQuestion(String topic, Long userId) {
        return topicRepository
                .findByUserIdAndNameNormalized(userId, ContentNormalizer.topicKey(topic))
                .map(Topic::getEmoji)
                .orElseThrow(() -> NotFoundException.build(Exceptions.NOT_FOUND));
    }

    @Override
    public void saveFeedback(FeedbackDTO feedbackDTO) {
        feedbackService.save(feedbackDTO);
    }
}
