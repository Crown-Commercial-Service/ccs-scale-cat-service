package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.NotSupportedException;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;

import javax.servlet.http.HttpServletResponse;
import javax.validation.ValidationException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/assessments", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class AssessmentsController extends AbstractRestController {

  private static final String ERR_FMT_REQ_IDS_NOT_MATCH =
      "requirement-id in body [%s] does not match requirement-id in path [%s]";

  private static final String ERR_EMPTY_BODY = "Empty body";
  private static final String NOT_SUPPORTED_MIME_TYPE  = "Mime Type application/json not supported";

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

    return assessmentService.getAssessment(assessmentId, Optional.of(principal));
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

  @GetMapping("/tools/{tool-id}/dimensions/{dimension-id}/data")
  public void getSupplierDimensionData(
      final @PathVariable("tool-id") Integer toolId,
      final @PathVariable("dimension-id") Integer dimensionId,
      @RequestParam(name = "mime-type", required = false, defaultValue = "text/csv")
          String mimeType, @RequestParam(name = "lot-id", required = false) Integer lotId,
      HttpServletResponse response, final JwtAuthenticationToken authentication)
      throws IOException {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getSupplierDimensionData invoked on behalf of principal: {}", principal);

    if (mimeType.equals(APPLICATION_JSON_VALUE)) {
      throw new NotSupportedException(NOT_SUPPORTED_MIME_TYPE);
    }

    var suppliers = assessmentService.getSupplierDimensionData(toolId, dimensionId, lotId);
    response.setContentType("text/csv");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=" + String.format(SUPPLIER_DATA, toolId, dimensionId));
    response.addHeader(HttpHeaders.PRAGMA, "no-cache");
    response.addHeader(HttpHeaders.EXPIRES, "0");
    response.getWriter().write(suppliers);

  }
}
