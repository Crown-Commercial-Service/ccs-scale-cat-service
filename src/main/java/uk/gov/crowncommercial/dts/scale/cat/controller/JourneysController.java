package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Collection;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.StringValueResponse;
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
public class JourneysController extends AbstractRestController {

  private final JourneyService journeyService;

  @PostMapping
  @TrackExecutionTime
  public ResponseEntity<StringValueResponse> createJourney(
      @RequestBody @Valid final Journey journey, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new StringValueResponse(journeyService.createJourney(journey, principal)));
  }
  
  @PutMapping
  @TrackExecutionTime
  public ResponseEntity<StringValueResponse> updateJourney(
      @RequestBody @Valid final Journey journey, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);

    return ResponseEntity.status(HttpStatus.OK)
        .body(new StringValueResponse(journeyService.updateJourney(journey, principal)));
  }

  @GetMapping("/{journey-id}/steps")
  @TrackExecutionTime
  public Collection<JourneyStepState> getJourneyState(
      @PathVariable("journey-id") final String journeyId) {
    return journeyService.getJourneyState(journeyId);
  }

  @PutMapping("/{journey-id}/steps/{step-id}")
  @TrackExecutionTime
  public StringValueResponse updateJourneyStepState(
      @PathVariable("journey-id") final String journeyId,
      @PathVariable("step-id") final Integer stepId, @RequestBody @Valid final StepState stepState,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    journeyService.updateJourneyStepState(journeyId, stepId, stepState, principal);
    return new StringValueResponse("OK");
  }

}
