package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.crowncommercial.dts.scale.cat.exception.DataConflictException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.JourneyEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.Journey;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.JourneyStepState;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.StepState;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 *
 */
@SpringBootTest(classes = {JourneyService.class}, webEnvironment = WebEnvironment.NONE)
class JourneyServiceTest {

  private static final String JOURNEY_ID = "ocds-abc-123";
  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final String CLIENT_ID = "CAT_BUYER_UI";

  @Autowired
  private JourneyService journeyService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @Test
  void testCreateJourney() {

    var journey = new Journey().journeyId(JOURNEY_ID)
        .addStatesItem(new JourneyStepState().step(1).state(StepState.NOT_STARTED))
        .addStatesItem(new JourneyStepState().step(2).state(StepState.OPTIONAL));

    var journeyId = journeyService.createJourney(journey, PRINCIPAL);

    assertEquals(JOURNEY_ID, journeyId);

    var captor = ArgumentCaptor.forClass(JourneyEntity.class);
    verify(retryableTendersDBDelegate, times(1)).save(captor.capture());
    var capturedJourney = captor.getValue();

    assertEquals(JOURNEY_ID, capturedJourney.getExternalId());
    assertEquals(CLIENT_ID, capturedJourney.getClientId());
    assertEquals(PRINCIPAL, capturedJourney.getCreatedBy());
    assertEquals(journey.getStates(), capturedJourney.getJourneyDetails());
  }

  @Test
  void testCreateJourneyAlreadyExists() {
    var journeyEntity = JourneyEntity.builder().id(1).externalId(JOURNEY_ID).build();
    var journey = new Journey().journeyId(JOURNEY_ID);
    when(retryableTendersDBDelegate.findJourneyByExternalId(JOURNEY_ID))
        .thenReturn(Optional.of(journeyEntity));

    var ex = assertThrows(DataConflictException.class,
        () -> journeyService.createJourney(journey, PRINCIPAL));

    assertEquals("Journey [" + JOURNEY_ID + "] already exists", ex.getMessage());
  }

  @Test
  void testUpdateJourney() {
    var journeyEntityStatesBefore =
        List.of(new JourneyStepState().step(1).state(StepState.NOT_STARTED),
            new JourneyStepState().step(2).state(StepState.OPTIONAL));

    var journeyEntityStatesAfter =
        List.of(new JourneyStepState().step(1).state(StepState.IN_PROGRESS),
            new JourneyStepState().step(2).state(StepState.OPTIONAL));

    var journeyEntity =
        JourneyEntity.builder().id(1).externalId(JOURNEY_ID).clientId("CAT_BUYER_UI")
            .createdBy(PRINCIPAL).journeyDetails(journeyEntityStatesBefore).build();

    when(retryableTendersDBDelegate.findJourneyByExternalId(JOURNEY_ID))
        .thenReturn(Optional.of(journeyEntity));

    // Invoke
    journeyService.updateJourneyStepState(JOURNEY_ID, 1, StepState.IN_PROGRESS, PRINCIPAL);

    var captor = ArgumentCaptor.forClass(JourneyEntity.class);
    verify(retryableTendersDBDelegate, times(1)).save(captor.capture());
    var capturedJourney = captor.getValue();

    assertEquals(JOURNEY_ID, capturedJourney.getExternalId());
    assertEquals(CLIENT_ID, capturedJourney.getClientId());
    assertEquals(PRINCIPAL, capturedJourney.getCreatedBy());
    assertEquals(PRINCIPAL, capturedJourney.getUpdatedBy());
    assertEquals(journeyEntityStatesAfter, capturedJourney.getJourneyDetails());
  }

  @Test
  void testUpdateJourneyNotFound() {
    when(retryableTendersDBDelegate.findJourneyByExternalId(JOURNEY_ID))
        .thenReturn(Optional.empty());

    var ex = assertThrows(ResourceNotFoundException.class, () -> journeyService
        .updateJourneyStepState(JOURNEY_ID, 1, StepState.IN_PROGRESS, PRINCIPAL));

    assertEquals("Journey [" + JOURNEY_ID + "] not found", ex.getMessage());
  }

  @Test
  void testGetJourneyState() {
    var journeyEntityStates = List.of(new JourneyStepState().step(1).state(StepState.NOT_STARTED),
        new JourneyStepState().step(2).state(StepState.OPTIONAL));
    var journeyEntity = JourneyEntity.builder().id(1).externalId(JOURNEY_ID)
        .clientId("CAT_BUYER_UI").createdBy(PRINCIPAL).journeyDetails(journeyEntityStates).build();

    when(retryableTendersDBDelegate.findJourneyByExternalId(JOURNEY_ID))
        .thenReturn(Optional.of(journeyEntity));

    // Invoke
    var journeyStates = journeyService.getJourneyState(JOURNEY_ID);

    assertEquals(journeyEntityStates, journeyStates);
  }

  @Test
  void testGetJourneyStateNotFound() {
    when(retryableTendersDBDelegate.findJourneyByExternalId(JOURNEY_ID))
        .thenReturn(Optional.empty());

    var ex = assertThrows(ResourceNotFoundException.class,
        () -> journeyService.getJourneyState(JOURNEY_ID));

    assertEquals("Journey [" + JOURNEY_ID + "] not found", ex.getMessage());
  }
}
