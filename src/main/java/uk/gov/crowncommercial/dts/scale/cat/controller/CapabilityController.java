package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentSummary;
import uk.gov.crowncommercial.dts.scale.cat.service.CapabilityService;


@RestController
@RequestMapping(path = "/capabilities/assessments", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class CapabilityController extends AbstractRestController {

  private final CapabilityService capabilityService;

  @PostMapping
  public Integer createAssessment(@RequestBody final Assessment assessment,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createAssessment invoked on behalf of principal: {}", principal);

    return capabilityService.createCapabilityAssessment(assessment);
  }


  @GetMapping
  public List<AssessmentSummary> getAssessmentsForUser(
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getAssessmentsForUser invoked on behalf of principal: {}", principal);

    return capabilityService.getAssessmentsForUser(principal);
  }

}
