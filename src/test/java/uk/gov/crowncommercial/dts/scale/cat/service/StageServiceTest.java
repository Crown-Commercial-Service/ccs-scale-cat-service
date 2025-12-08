package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.StageException;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.Stages;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageDataEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageTypesEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.StageDataRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.StageTypesRepo;

/**
 * QuestionAndAnswerService Service layer tests
 */
@ExtendWith(MockitoExtension.class)
class StageServiceTest {

	private static final String EVENT_ID = "eventId";
    private static final String NO_MATCH_EVENT_ID = "NoMatchEventId";

    private static final String MODULE_1 = "Module 1 - Initial Stages of a Multi Stage Competitive Selection Process";
    private static final String MODULE_2 = "Module 2 - Conditions of Participation Assessment";
    private static final String MODULE_3 = "Module 3 - Tendering Stage";
    private static final String MODULE_4 = "Module 4 - Presentation/Demonstration Stage";
    private static final String MODULE_5 = "Module 5 – Site Visit Stage";
    private static final String MODULE_6 = "Module 6 – Dialogue Stage";
    private static final String MODULE_7 = "Module 7 – Negotiation Stage";
    private static final String MODULE_8 = "Module 8 - Final Tendering Stage";

  @Mock
  private StageTypesRepo stageTypesRepo;

  @Mock
  private StageDataRepo stageDataRepo;

  @InjectMocks
  private StageService stageService;

  @Test
  void shouldReturnAllStageTypes() throws Exception {
    // Mock behaviours
    when(stageTypesRepo.findAll())
        .thenReturn(List.of(
            StageTypesEntity.builder().id(1L).stageType(MODULE_1).build(),
            StageTypesEntity.builder().id(2L).stageType(MODULE_2).build(),
            StageTypesEntity.builder().id(3L).stageType(MODULE_3).build(),
            StageTypesEntity.builder().id(4L).stageType(MODULE_4).build(),
            StageTypesEntity.builder().id(5L).stageType(MODULE_5).build(),
            StageTypesEntity.builder().id(6L).stageType(MODULE_6).build(),
            StageTypesEntity.builder().id(7L).stageType(MODULE_7).build(),
            StageTypesEntity.builder().id(8L).stageType(MODULE_8).build()));

    // Invoke
    final var response = stageService.getStageTypes();

    // Assert
    assertAll(() -> assertNotNull(response),
              () -> assertNotNull(response.getStageTypes()),
              () -> assertEquals(8, response.getStageTypes().size()),
              () -> assertEquals(MODULE_1, response.getStageTypes().get(0).getStageType()),
              () -> assertEquals(MODULE_2, response.getStageTypes().get(1).getStageType()),
              () -> assertEquals(MODULE_3, response.getStageTypes().get(2).getStageType()),
              () -> assertEquals(MODULE_4, response.getStageTypes().get(3).getStageType()),
              () -> assertEquals(MODULE_5, response.getStageTypes().get(4).getStageType()),
              () -> assertEquals(MODULE_6, response.getStageTypes().get(5).getStageType()),
              () -> assertEquals(MODULE_7, response.getStageTypes().get(6).getStageType()),
              () -> assertEquals(MODULE_8, response.getStageTypes().get(7).getStageType()));

    // Verify
    verify(stageTypesRepo).findAll();
  }

  @Test
  void shouldReturnCorrectStageTypesForValidEventId() throws Exception {
    // Mock behaviours
    when(stageDataRepo.findById(EVENT_ID))
        .thenReturn(Optional.of(StageDataEntity.builder()
                .id(1L)
                .eventId(EVENT_ID)
                .numberOfStages(2)
                .stageIds(List.of(1L, 2L))
            .build()));

    // Invoke
    final var response = stageService.getStagesForEventId(EVENT_ID);

    // Assert
    assertAll(() -> assertNotNull(response),
              () -> assertEquals(2, response.getNumberOfStages()),
              () -> assertEquals(1L, response.getStages().get(0).getId()),
              () -> assertEquals(2L, response.getStages().get(1).getId()));

    // Verify
    verify(stageDataRepo).findById(EVENT_ID);
  }

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
  void shouldThrowExceptionForNoMatchOnEventId() throws Exception {
    // Mock behaviours
    when(stageDataRepo.findById(NO_MATCH_EVENT_ID))
        .thenReturn(Optional.empty());

    // Invoke
    final var ex = assertThrows(ResourceNotFoundException.class,
        () -> stageService.getStagesForEventId(NO_MATCH_EVENT_ID));

    // Assert
    assertEquals("No stage data found for eventId: " + NO_MATCH_EVENT_ID, ex.getMessage());

    // Verify
    verify(stageDataRepo).findById(NO_MATCH_EVENT_ID);
  }

  @Test
  void shouldCreateStagesForValidEventId() throws Exception {
    // Mock behaviours
    when(stageDataRepo.save(any(StageDataEntity.class)))
        .thenReturn(StageDataEntity.builder()
                .id(1L)
                .eventId(EVENT_ID)
                .numberOfStages(3)
                .stageIds(List.of(2L, 4L, 6L))
            .build());

    // Invoke
    final var stagesWrite = new StagesWrite();
    stagesWrite.addStagesItem(new Stages().id(2L));
    stagesWrite.addStagesItem(new Stages().id(4L));
    stagesWrite.addStagesItem(new Stages().id(6L));

    final var response = stageService.createOrUpdateStagesForEventId(EVENT_ID, stagesWrite);

    // Assert
    assertTrue(response);

    // Verify
    verify(stageDataRepo).save(any(StageDataEntity.class));
  }

  @Test
  void shouldThrowExceptionForCreateStagesForInvalidEventId() throws Exception {
    final var stagesWrite = new StagesWrite();
    stagesWrite.addStagesItem(new Stages().id(2L));
    stagesWrite.addStagesItem(new Stages().id(4L));
    stagesWrite.addStagesItem(new Stages().id(6L));

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
    final var stagesWrite = new StagesWrite().stages(null);

    // Invoke
    final var ex = assertThrows(StageException.class,
        () -> stageService.createOrUpdateStagesForEventId(EVENT_ID, stagesWrite));

    // Assert
    assertEquals(
        "Stage Service application exception, Code: [N/A], Message: [Cannot save stage data, invalid data for eventId: " + EVENT_ID + "]",
        ex.getMessage());
  }

  @Test
  void shouldThrowExceptionForCreateStagesForEmptyStageIds() throws Exception {
    final var stagesWrite = new StagesWrite().stages(List.of());

    // Invoke
    final var ex = assertThrows(StageException.class,
        () -> stageService.createOrUpdateStagesForEventId(EVENT_ID, stagesWrite));

    // Assert
    assertEquals(
        "Stage Service application exception, Code: [N/A], Message: [Cannot save stage data, invalid data for eventId: " + EVENT_ID + "]",
        ex.getMessage());
  }
}
