package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ValidationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.NotSupportedException;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.SupplierSubmissionData;
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
  private static final String NOT_SUPPORTED_MIME_TYPE = "Mime Type application/json not supported";

  private final AssessmentService assessmentService;

  @GetMapping("/tools/{tool-id}/dimensions")
  @TrackExecutionTime
  public List<DimensionDefinition> getDimensions(final @PathVariable("tool-id") Integer toolId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getDimensions invoked on behalf of principal: {}", principal);

    return assessmentService.getDimensions(toolId);
  }

  @PostMapping
  @TrackExecutionTime
  public Integer createAssessment(@RequestBody final Assessment assessment,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createAssessment invoked on behalf of principal: {}", principal);

    return assessmentService.createAssessment(assessment, principal);
  }

  @GetMapping
  @TrackExecutionTime
  public List<AssessmentSummary> getAssessmentsForUser(final @RequestParam(required = false, name = "external-tool-id") Integer externalToolId, final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    log.info("getAssessmentsForUser invoked on behalf of principal: {}", principal);

    return assessmentService.getAssessmentsForUser(principal, externalToolId);
  }

  @GetMapping("/{assessment-id}")
  @TrackExecutionTime
  public Assessment getAssessment(final @PathVariable("assessment-id") Integer assessmentId,
      @RequestParam(defaultValue = "false", required = true) final Boolean scores,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getAssessment invoked on behalf of principal: {}", principal);

    return assessmentService.getAssessment(assessmentId, scores, Optional.of(principal));
  }

  @PutMapping("/{assessment-id}/dimensions/{dimension-id}")
  @TrackExecutionTime
  public Integer createUpdateDimension(final @PathVariable("assessment-id") Integer assessmentId,
      final @PathVariable("dimension-id") Integer dimensionId,
      @RequestBody final DimensionRequirement dimensionRequirement,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createUpdateDimension invoked on behalf of principal: {}", principal);

    return assessmentService.updateDimension(assessmentId, dimensionId, dimensionRequirement,
        principal,true);
  }

  @PutMapping("/{assessment-id}/dimensions")
  @TrackExecutionTime
  public String createUpdateDimensions(final @PathVariable("assessment-id") Integer assessmentId,
      @RequestBody final DimensionRequirement[] dimensionRequirement,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createUpdateDimension invoked on behalf of principal: {}", principal);

    assessmentService.updateDimensions(assessmentId, dimensionRequirement,
        principal);
    return Constants.OK_MSG;
  }

  @PutMapping("/{assessment-id}/dimensions/{dimension-id}/requirements/{requirement-id}")
  @TrackExecutionTime
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
  @TrackExecutionTime
  public String deleteRequirement(final @PathVariable("assessment-id") Integer assessmentId,
      final @PathVariable("dimension-id") Integer dimensionId,
      final @PathVariable("requirement-id") Integer requirementId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("deleteRequirement invoked on behalf of principal: {}", principal);

    assessmentService.deleteRequirement(assessmentId, dimensionId, requirementId, principal);

    return Constants.OK_MSG;
  }

  @GetMapping(value = "/tools/{tool-id}/dimensions/{dimension-id}/data", produces = {"text/csv", "application/json"})
  @TrackExecutionTime
  public ResponseEntity<InputStreamResource> getSupplierDimensionData(
      final @PathVariable("tool-id") String toolId,
      final @PathVariable("dimension-id") Integer dimensionId,
      @RequestParam(name = "lot-id", required = false) final String lotId,
      @RequestParam(name = "suppliers", required = false) final List<String> suppliers,
      @RequestHeader(name = "mime-type", required = false,
          defaultValue = "text/csv") final String mimeType,
      final HttpServletResponse response, final JwtAuthenticationToken authentication)
      throws IOException {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getSupplierDimensionData invoked on behalf of principal: {}", principal);

    var supplierDimensionData =
        assessmentService.getSupplierDimensions(toolId, dimensionId, lotId, suppliers);


    if(mimeType.equalsIgnoreCase("text/csv"))
      return printToCsv(supplierDimensionData, String.format(SUPPLIER_DATA, toolId, dimensionId));
    else if(mimeType.equalsIgnoreCase("application/json"))
      return printToJson(supplierDimensionData);
    else throw new NotSupportedException(NOT_SUPPORTED_MIME_TYPE);
  }

  @SneakyThrows
  private ResponseEntity<InputStreamResource> printToJson(Set<SupplierSubmissionData> supplierDimensionData) {
    ObjectMapper maper = new ObjectMapper();
    var out = new ByteArrayOutputStream();
    maper.writer().writeValue(out, supplierDimensionData);
    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/json"))
            .body(new InputStreamResource(new ByteArrayInputStream(out.toByteArray())));
  }


  private ResponseEntity<InputStreamResource> printToCsv(Set<SupplierSubmissionData> supplierDimensionData, String filename) {
    var out = new ByteArrayOutputStream();
    try (var csvPrinter = new CSVPrinter(new PrintWriter(out), CSVFormat.DEFAULT)) {
      csvPrinter.printRecord("SupplierId", "RequirementName", "DimensionName", "AssessmentToolName",
              "SubmissionTypeName", "SubmissionValue");
      for (SupplierSubmissionData calculationBase : supplierDimensionData) {
        writeRecord(calculationBase, csvPrinter);
      }
      csvPrinter.flush();
    } catch (IOException e) {
      throw new RuntimeException("fail to import data to CSV file: " + e.getMessage());
    }

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=" + filename)
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(new InputStreamResource(new ByteArrayInputStream(out.toByteArray())));
  }

  private void writeRecord(final SupplierSubmissionData ssd, final CSVPrinter csvPrinter)
      throws IOException {
    csvPrinter.printRecord(ssd.getSupplierId(), ssd.getRequirementName(),
        ssd.getDimensionName(), ssd.getToolName(),
        ssd.getSubmissionTypeName(), ssd.getSubmissionValue()
        );

  }
}
