package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Collection;
import java.util.Set;
import javax.websocket.server.PathParam;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;
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
public class JourneysController {

  private final JourneyService journeyService;

  @PostMapping
  public String createJourney(@RequestBody final Journey journey) {
    return "journey-abc";
  }

  @GetMapping("/{journey-id}/steps")
  public Collection<JourneyStepState> getJourneyState(
      @PathParam("journey-id") final String journeyId) {
    return Set.of(new JourneyStepState());
  }

  @PutMapping("/{journey-id}/steps/{step-id}")
  public StepState updateJourneyStepState(@PathParam("journey-id") final String journeyId,
      @PathParam("step-id") final Integer stepId) {
    return StepState.NOT_STARTED;
  }

}
