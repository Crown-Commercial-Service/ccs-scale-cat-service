package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Collection;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.Journey;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.JourneyStepState;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.StepState;
import uk.gov.crowncommercial.dts.scale.cat.service.JourneyService;

/**
 * Journey service controller
 */
@RestController
@RequestMapping(path = "/journeys", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class JourneysController extends AbstractRestController {

  private final JourneyService journeyService;

  @PostMapping
  public String createJourney(@RequestBody final Journey journey) {
    return journeyService.createJourney(journey);
  }

  @GetMapping("/{journey-id}/steps")
  public Collection<JourneyStepState> getJourneyState(
      @PathVariable("journey-id") final String journeyId) {
    return journeyService.getJourneyState(journeyId);
  }

  @PutMapping("/{journey-id}/steps/{step-id}")
  public void updateJourneyStepState(@PathVariable("journey-id") final String journeyId,
      @PathVariable("step-id") final Integer stepId, @RequestBody final StepState stepState) {
    journeyService.updateJourneyStepState(journeyId, stepId, stepState);
  }

}
