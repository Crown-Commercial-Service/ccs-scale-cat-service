package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageTypesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesWrite;
import uk.gov.crowncommercial.dts.scale.cat.service.StageService;

/**
 * Stage Controller to initiate requests to the Stage DB tables
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/stages", produces = APPLICATION_JSON_VALUE)
public class StageController extends AbstractRestController {

  private final StageService stageService;

  @GetMapping("/types")
  @TrackExecutionTime
  public ResponseEntity<StageTypesRead> getStageTypes(final JwtAuthenticationToken authentication) {
    log.info("getStageTypes invoked");
    return ResponseEntity.ok(stageService.getStageTypes());
  }

  @GetMapping("/event/{event-id}")
  @TrackExecutionTime
  public ResponseEntity<StagesRead> getStagesForEventId(
      @PathVariable("event-id") final String eventId,
      final JwtAuthenticationToken authentication) {
    log.info("getStagesForEventId invoked for eventId: {}", eventId);
    return ResponseEntity.ok(stageService.getStagesForEventId(eventId));
  }

  @PostMapping("/event/{event-id}")
  @TrackExecutionTime
  public ResponseEntity<Boolean> createOrUpdateStagesForEventId(
      @PathVariable("event-id") final String eventId,
      @Valid @RequestBody final StagesWrite stagesWrite,
      final JwtAuthenticationToken authentication) {
    log.info("createOrUpdateStagesForEventId invoked for eventId: {}", eventId);
    return ResponseEntity.ok(stageService.createOrUpdateStagesForEventId(eventId, stagesWrite));
  }
}
