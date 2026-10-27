package com.example.proyecto2025_BE.unittests;


import com.example.proyecto2025_BE.repository.FeedbackRepository;
import com.example.proyecto2025_BE.model.Feedback;
import com.example.proyecto2025_BE.model.dto.FeedbackDTO;
import com.example.proyecto2025_BE.service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @InjectMocks
    private FeedbackService feedbackService;


    @Test
    void testSavePositiveFeedback() {

        FeedbackDTO positiveFeedbackDTO = createPositiveFeedbackDTO();
        Feedback expectedFeedback = Feedback.fromDTO(positiveFeedbackDTO);

        when(feedbackRepository.save(any(Feedback.class))).thenReturn(expectedFeedback);
        feedbackService.save(positiveFeedbackDTO);

        verify(feedbackRepository, times(1)).save(argThat(feedback ->
                feedback.getUserId().equals(positiveFeedbackDTO.userId()) &&
                        feedback.getQuestionId().equals(positiveFeedbackDTO.questionId()) &&
                        feedback.getFeedbackType().equals(Feedback.FeedbackOption.POSITIVE)
        ));
    }

    @Test
    void testSaveNegativeFeedback() {
        FeedbackDTO negativeFeedbackDTO = createNegativeFeedbackDTO();
        Feedback expectedFeedback = Feedback.fromDTO(negativeFeedbackDTO);

        when(feedbackRepository.save(any(Feedback.class))).thenReturn(expectedFeedback);

        feedbackService.save(negativeFeedbackDTO);

        verify(feedbackRepository, times(1)).save(argThat(feedback ->
                feedback.getUserId().equals(negativeFeedbackDTO.userId()) &&
                        feedback.getQuestionId().equals(negativeFeedbackDTO.questionId()) &&
                        feedback.getFeedbackType().equals(Feedback.FeedbackOption.NEGATIVE) &&
                        (negativeFeedbackDTO.description() == null
                                || feedback.getDescription().equals(negativeFeedbackDTO.description()))
        ));
    }


    public static FeedbackDTO createPositiveFeedbackDTO() {
        return FeedbackDTO.builder()
                .userId(1L)
                .questionId(10L)
                .feedbackType(Feedback.FeedbackOption.POSITIVE)
                .build();
    }

    public static FeedbackDTO createNegativeFeedbackDTO() {
        return FeedbackDTO.builder()
                .userId(2L)
                .questionId(10L)
                .feedbackType(Feedback.FeedbackOption.NEGATIVE)
                .description("This is a negative feedback")
                .build();
    }

}
