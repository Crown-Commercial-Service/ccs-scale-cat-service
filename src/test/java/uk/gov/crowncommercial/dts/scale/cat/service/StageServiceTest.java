package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.gov.crowncommercial.dts.scale.cat.exception.StageException;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageDataEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.StageDataRepo;

/**
 * QuestionAndAnswerService Service layer tests
 */
@ExtendWith(MockitoExtension.class)
class StageServiceTest {

	private static final String EVENT_ID = "eventId";
    private static final String NO_MATCH_EVENT_ID = "NoMatchEventId";

  @Mock
  private StageDataRepo stageDataRepo;

  @InjectMocks
  private StageService stageService;

  @Test
  void shouldThrowExceptionForInvalidEventId() throws Exception {
    // Invoke
    final var ex = assertThrows(StageException.class,
        () -> stageService.getStagesForEventId(""));

    // Assert
    assertEquals(
        "Stage Service application exception, Code: [N/A], Message: [Cannot retrieve stage data, invalid eventId]",
        ex.getMessage());
  }

  @Test
  void shouldReturnEmptyStructForNoMatchOnEventId() throws Exception {
    // Mock behaviours
    when(stageDataRepo.findByEventId(NO_MATCH_EVENT_ID))
        .thenReturn(Optional.empty());

    // Invoke
    final var response = stageService.getStagesForEventId(NO_MATCH_EVENT_ID);

    // Assert
    assertAll(() -> assertNotNull(response),
              () -> assertEquals(0, response.getNumberOfStages()));

    // Verify
    verify(stageDataRepo).findByEventId(NO_MATCH_EVENT_ID);
  }

  @Test
  void shouldCreateStagesForValidEventId() throws Exception {
    // Mock behaviours
//    when(stageDataRepo.save(any(StageDataEntity.class)))
//        .thenReturn(StageDataEntity.builder()
//                .id(1)
//                .eventId(EVENT_ID)
//                .numberOfStages(3)
//            .build());

    // Invoke
//    final var stagesWrite = new StagesWrite();

    // TODO - BM - NCAS-844 - fixme

//    final var response = stageService.createOrUpdateStagesForEventId(EVENT_ID, stagesWrite);

    // Assert
//    assertTrue(response);

    // Verify
//    verify(stageDataRepo).save(any(StageDataEntity.class));
  }

  @Test
  void shouldThrowExceptionForCreateStagesForInvalidEventId() throws Exception {
    final var stagesWrite = new StagesWrite();

    // Invoke
    final var ex = assertThrows(StageException.class,
        () -> stageService.createOrUpdateStagesForEventId(null, stagesWrite));

    // Assert
    assertEquals(
        "Stage Service application exception, Code: [N/A], Message: [Cannot save stage data, invalid data for eventId: null]",
        ex.getMessage());
  }

  @Test
  void shouldThrowExceptionForCreateStagesForNullStageIds() throws Exception {
    final var stagesWrite = new StagesWrite();

    // TODO - BM - NCAS-844 - fixme

    // Invoke
//    final var ex = assertThrows(StageException.class,
//        () -> stageService.createOrUpdateStagesForEventId(EVENT_ID, stagesWrite));

    // Assert
//    assertEquals(
//        "Stage Service application exception, Code: [N/A], Message: [Cannot save stage data, invalid data for eventId: " + EVENT_ID + "]",
//        ex.getMessage());
  }

  @Test
  void shouldThrowExceptionForCreateStagesForEmptyStageIds() throws Exception {
    final var stagesWrite = new StagesWrite();

    // TODO - BM - NCAS-844 - fixme

    // Invoke
//    final var ex = assertThrows(StageException.class,
//        () -> stageService.createOrUpdateStagesForEventId(EVENT_ID, stagesWrite));

    // Assert
//    assertEquals(
//        "Stage Service application exception, Code: [N/A], Message: [Cannot save stage data, invalid data for eventId: " + EVENT_ID + "]",
//        ex.getMessage());
  }
}
