package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.JourneyEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.Journey;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.JourneyStepState;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.StepState;
import uk.gov.crowncommercial.dts.scale.cat.repo.JourneyRepo;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class JourneyService {

  private static final String ERR_FMT_JOURNEY_NOT_FOUND = "Journey [%s] not found";
  private final JourneyRepo journeyRepo;

  public String createJourney(final Journey journey) {

    var journeyEntity = JourneyEntity.builder().clientId("CAT_BUYER_UI")
        .externalId(journey.getJourneyId()).journeyDetails(journey.getStates()).createdBy("tom")
        .createdAt(Instant.now()).updatedBy("tom").updatedAt(Instant.now()).build();

    journeyRepo.save(journeyEntity);
    return journey.getJourneyId();
  }

  public Collection<JourneyStepState> getJourneyState(final String journeyId) {
    var journey = findJourney(journeyId);
    return journey.getJourneyDetails();
  }

  public void updateJourneyStepState(final String journeyId, final Integer stepId,
      final StepState stepState) {
    var journey = findJourney(journeyId);
    var journeyStepStates = journey.getJourneyDetails();

    var journeyStepState = journeyStepStates.stream()
        .filter(jss -> Objects.equals(stepId, jss.getStep())).findFirst().orElseThrow();
    journeyStepState.setState(stepState);

    journeyRepo.save(journey);
  }

  private JourneyEntity findJourney(final String journeyId) {
    var journey = journeyRepo.findByExternalId(journeyId);

    return journey.orElseThrow(
        () -> new ResourceNotFoundException(String.format(ERR_FMT_JOURNEY_NOT_FOUND, journeyId)));
  }

}
