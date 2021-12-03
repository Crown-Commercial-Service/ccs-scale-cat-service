package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.Journey;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.JourneyStepState;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.StepState;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class JourneyService {

  public String createJourney(final Journey journey) {
    return "journey-abc";
  }

  public Collection<JourneyStepState> getJourneyState(final String journeyId) {
    return Set.of(new JourneyStepState());
  }

  public StepState updateJourneyStepState(final String journeyId, final Integer stepId) {
    return StepState.NOT_STARTED;
  }

}
