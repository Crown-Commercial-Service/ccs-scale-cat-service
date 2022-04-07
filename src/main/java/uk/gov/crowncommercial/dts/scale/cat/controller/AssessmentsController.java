package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.List;
import javax.validation.ValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;

@RestController
@RequestMapping(path = "/assessments", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class AssessmentsController extends AbstractRestController {

  private static final String ERR_FMT_REQ_IDS_NOT_MATCH =
      "requirement-id in body [%s] does not match requirement-id in path [%s]";

  private static final String ERR_EMPTY_BODY = "Empty body";

  private final AssessmentService assessmentService;

  @GetMapping("/tools/{tool-id}/dimensions")
  public List<DimensionDefinition> getDimensions(final @PathVariable("tool-id") Integer toolId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getDimensions invoked on behalf of principal: {}", principal);

    return assessmentService.getDimensions(toolId);
  }

  @PostMapping
  public Integer createAssessment(@RequestBody final Assessment assessment,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createAssessment invoked on behalf of principal: {}", principal);

    return assessmentService.createAssessment(assessment, principal);
  }

  @GetMapping
  public List<AssessmentSummary> getAssessmentsForUser(
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getAssessmentsForUser invoked on behalf of principal: {}", principal);

    return assessmentService.getAssessmentsForUser(principal);
  }

  @GetMapping("/{assessment-id}")
  public Assessment getAssessment(final @PathVariable("assessment-id") Integer assessmentId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getAssessment invoked on behalf of principal: {}", principal);

    return assessmentService.getAssessment(assessmentId, principal);
  }

  @PutMapping("/{assessment-id}/dimensions/{dimension-id}")
  public Integer createUpdateDimension(final @PathVariable("assessment-id") Integer assessmentId,
      final @PathVariable("dimension-id") Integer dimensionId,
      @RequestBody final DimensionRequirement dimensionRequirement,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createUpdateDimension invoked on behalf of principal: {}", principal);

    return assessmentService.updateDimension(assessmentId, dimensionId, dimensionRequirement,
        principal);
  }

  @PutMapping("/{assessment-id}/dimensions/{dimension-id}/requirements/{requirement-id}")
  public Integer createUpdateRequirement(final @PathVariable("assessment-id") Integer assessmentId,
      final @PathVariable("dimension-id") Integer dimensionId,
      final @PathVariable("requirement-id") Integer requirementId,
      @RequestBody final Requirement requirement, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createUpdateRequirement invoked on behalf of principal: {}", principal);

    if (requirement.getWeighting() == null) {
      throw new ValidationException(ERR_EMPTY_BODY);
    }
    // Requirement id in body is redundant - allow it to be optionally omitted, and use path param
    if (requirement.getRequirementId() == null) {
      requirement.setRequirementId(requirementId);
    }

    // Check in casde it has been supplied, but is not same as path param
    if (!requirement.getRequirementId().equals(requirementId)) {
      throw new ValidationException(
          String.format(ERR_FMT_REQ_IDS_NOT_MATCH, requirement.getRequirementId(), requirementId));
    }

    return assessmentService.updateRequirement(assessmentId, dimensionId, requirement, principal);
  }

  @DeleteMapping("/{assessment-id}/dimensions/{dimension-id}/requirements/{requirement-id}")
  public String deleteRequirement(final @PathVariable("assessment-id") Integer assessmentId,
      final @PathVariable("dimension-id") Integer dimensionId,
      final @PathVariable("requirement-id") Integer requirementId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("deleteRequirement invoked on behalf of principal: {}", principal);

    assessmentService.deleteRequirement(assessmentId, dimensionId, requirementId, principal);

    return Constants.OK_MSG;
  }
}
