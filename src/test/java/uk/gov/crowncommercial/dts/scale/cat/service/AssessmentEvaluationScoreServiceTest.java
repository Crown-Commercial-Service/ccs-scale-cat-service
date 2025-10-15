package uk.gov.crowncommercial.dts.scale.cat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AssessmentEvaluationScoreRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AssessmentEvaluationScoreResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.AssessmentEvaluationScore;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.repo.AssessmentEvaluationScoreRepo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentEvaluationScoreServiceTest {

    @Mock
    private AssessmentEvaluationScoreRepo assessmentEvaluationScoreRepo;

    @InjectMocks
    private AssessmentEvaluationScoreService assessmentEvaluationScoreService;

    private AssessmentEvaluationScoreRequest testRequest;
    private AssessmentEvaluationScore testEntity;
    private String testPrincipal = "test@example.com";

    @BeforeEach
    void setUp() {
        testRequest = new AssessmentEvaluationScoreRequest();
        testRequest.setProjectId(12345);
        testRequest.setEventId(483883);
        testRequest.setFrameworkAgreement("RM12345");
        testRequest.setQuestionId(1);
        testRequest.setAssessorEmailId("zahid@yopmail.com");
        testRequest.setAssessorScore(25);
        testRequest.setAssessorComment("String comment Text");

        Timestamps timestamps = Timestamps.createTimestamps(testPrincipal);

        testEntity = AssessmentEvaluationScore.builder()
                .id(1)
                .projectId(12345)
                .eventId(483883)
                .frameworkAgreement("RM12345")
                .questionId(1)
                .assessorEmailId("zahid@yopmail.com")
                .assessorScore(25)
                .assessorComment("String comment Text")
                .timestamps(timestamps)
                .build();
    }

    @Test
    void createOrUpdateEvaluationScore_WhenScoreDoesNotExist_ShouldCreateNewScore() {
        // Given
        when(assessmentEvaluationScoreRepo.findByProjectIdAndEventIdAndQuestionIdAndAssessorEmailId(
                anyInt(), anyInt(), anyInt(), anyString())).thenReturn(Optional.empty());
        when(assessmentEvaluationScoreRepo.save(any(AssessmentEvaluationScore.class))).thenReturn(testEntity);

        // When
        AssessmentEvaluationScoreResponse response = assessmentEvaluationScoreService
                .createOrUpdateEvaluationScore(testRequest, testPrincipal);

        // Then
        assertNotNull(response);
        assertEquals(testEntity.getId(), response.getId());
        assertEquals(testEntity.getProjectId(), response.getProjectId());
        assertEquals(testEntity.getEventId(), response.getEventId());
        assertEquals(testEntity.getFrameworkAgreement(), response.getFrameworkAgreement());
        assertEquals(testEntity.getQuestionId(), response.getQuestionId());
        assertEquals(testEntity.getAssessorEmailId(), response.getAssessorEmailId());
        assertEquals(testEntity.getAssessorScore(), response.getAssessorScore());
        assertEquals(testEntity.getAssessorComment(), response.getAssessorComment());

        verify(assessmentEvaluationScoreRepo).save(any(AssessmentEvaluationScore.class));
    }

    @Test
    void createOrUpdateEvaluationScore_WhenScoreExists_ShouldUpdateExistingScore() {
        // Given
        when(assessmentEvaluationScoreRepo.findByProjectIdAndEventIdAndQuestionIdAndAssessorEmailId(
                anyInt(), anyInt(), anyInt(), anyString())).thenReturn(Optional.of(testEntity));
        when(assessmentEvaluationScoreRepo.save(any(AssessmentEvaluationScore.class))).thenReturn(testEntity);

        // When
        AssessmentEvaluationScoreResponse response = assessmentEvaluationScoreService
                .createOrUpdateEvaluationScore(testRequest, testPrincipal);

        // Then
        assertNotNull(response);
        assertEquals(testEntity.getId(), response.getId());
        verify(assessmentEvaluationScoreRepo).save(any(AssessmentEvaluationScore.class));
    }

    @Test
    void getEvaluationScoresByProjectAndEvent_ShouldReturnScores() {
        // Given
        List<AssessmentEvaluationScore> scores = List.of(testEntity);
        when(assessmentEvaluationScoreRepo.findByProjectIdAndEventId(anyInt(), anyInt())).thenReturn(scores);

        // When
        List<AssessmentEvaluationScoreResponse> response = assessmentEvaluationScoreService
                .getEvaluationScoresByProjectAndEvent(12345, 483883);

        // Then
        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(testEntity.getId(), response.get(0).getId());
    }

    @Test
    void getEvaluationScore_WhenScoreExists_ShouldReturnScore() {
        // Given
        when(assessmentEvaluationScoreRepo.findByProjectIdAndEventIdAndQuestionIdAndAssessorEmailId(
                anyInt(), anyInt(), anyInt(), anyString())).thenReturn(Optional.of(testEntity));

        // When
        AssessmentEvaluationScoreResponse response = assessmentEvaluationScoreService
                .getEvaluationScore(12345, 483883, 1, "zahid@yopmail.com");

        // Then
        assertNotNull(response);
        assertEquals(testEntity.getId(), response.getId());
    }

    @Test
    void getEvaluationScore_WhenScoreDoesNotExist_ShouldThrowException() {
        // Given
        when(assessmentEvaluationScoreRepo.findByProjectIdAndEventIdAndQuestionIdAndAssessorEmailId(
                anyInt(), anyInt(), anyInt(), anyString())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
                assessmentEvaluationScoreService.getEvaluationScore(12345, 483883, 1, "zahid@yopmail.com"));
    }
}

