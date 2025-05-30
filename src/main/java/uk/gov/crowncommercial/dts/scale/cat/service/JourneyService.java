package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.DataConflictException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.JourneyEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.Journey;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.JourneyStepState;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.StepState;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * Journey service - for interacting with journeys and journey steps
 */
@Service
@RequiredArgsConstructor
public class JourneyService {

  private static final String CLIENT_ID = "CAT_BUYER_UI";
  private static final String ERR_FMT_JOURNEY_NOT_FOUND = "";
  private static final String ERR_FMT_JOURNEY_STEP_NOT_FOUND = "Journey step [%s] not found";
  private static final String ERR_FMT_JOURNEY_EXISTS = "Journey [%s] already exists";
  private static final String ERR_FML_JOURNEY_NOT_EXIST = "Journey [%s] does not exist";
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  public String createJourney(final Journey journey, final String principal) {

    if (retryableTendersDBDelegate.findJourneyByExternalId(journey.getJourneyId()).isPresent()) {
      throw new DataConflictException(String.format(ERR_FMT_JOURNEY_EXISTS, journey.getJourneyId()));
    }

    var journeyEntity = JourneyEntity.builder().clientId(CLIENT_ID)
        .externalId(journey.getJourneyId()).journeyDetails(journey.getStates()).createdBy(principal)
        .createdAt(Instant.now()).build();

    retryableTendersDBDelegate.save(journeyEntity);
    return journey.getJourneyId();
  }
  
  public String updateJourney(final Journey journey, final String principal) {

    Optional<JourneyEntity> findJourneyByExternalId =
        retryableTendersDBDelegate.findJourneyByExternalId(journey.getJourneyId());

    if (findJourneyByExternalId.isEmpty()) {
      throw new ResourceNotFoundException(String.format(ERR_FML_JOURNEY_NOT_EXIST,  journey.getJourneyId()));
    }
    
    JourneyEntity journeyEntity = findJourneyByExternalId.get();
    journeyEntity.setJourneyDetails(journey.getStates());
    journeyEntity.setUpdatedAt(Instant.now());
    journeyEntity.setUpdatedBy(principal);

    retryableTendersDBDelegate.save(journeyEntity);
    return journey.getJourneyId();
  }

  public Collection<JourneyStepState> getJourneyState(final String journeyId) {
    var journey = findJourney(journeyId);
    return journey.getJourneyDetails();
  }

  public void updateJourneyStepState(final String journeyId, final Integer stepId,
      final StepState stepState, final String principal) {
    var journey = findJourney(journeyId);
    var journeyStepStates = journey.getJourneyDetails();

    var journeyStepState =
        journeyStepStates.stream().filter(jss -> Objects.equals(stepId, jss.getStep())).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format(ERR_FMT_JOURNEY_STEP_NOT_FOUND, stepId)));
    journeyStepState.setState(stepState);
    journey.setUpdatedBy(principal);
    journey.setUpdatedAt(Instant.now());
    retryableTendersDBDelegate.save(journey);
  }

  private JourneyEntity findJourney(final String journeyId) {
    var journey = retryableTendersDBDelegate.findJourneyByExternalId(journeyId);

    return journey.orElseThrow(
        () -> new ResourceNotFoundException(ERR_FMT_JOURNEY_NOT_FOUND));
  }
}