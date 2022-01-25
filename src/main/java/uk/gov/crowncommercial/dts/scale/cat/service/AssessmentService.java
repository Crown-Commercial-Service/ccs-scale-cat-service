package uk.gov.crowncommercial.dts.scale.cat.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionDefinition;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentService {

  private static final String ERR_FMT_TOOL_NOT_FOUND = "Assessment Tool [%s] not found";
  private static final String ERR_FMT_ASSESSMENT_NOT_FOUND = "Assessment [%s] not found";

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  /**
   *
   * @param toolId
   * @return
   */
  public Set<DimensionDefinition> getDimensions(final Integer toolId) {

    Set<Dimension> dimensions = retryableTendersDBDelegate.findDimensionsByToolId(toolId);

    return dimensions.stream().map(d -> {
      var dd = new DimensionDefinition();
      dd.setName(d.getName());
      return dd;
    }).collect(Collectors.toSet());
  }

  /**
   *
   * @param assessment
   * @param principal
   * @return
   */
  public Integer createAssessment(final Assessment assessment, final String principal) {

    // TODO - can we change 'internalName' to internalId/externalId/key or something maybe? Do we
    // need to change API reference? Have we a ticket to add this to Agreements Service?
    AssessmentTool tool =
        retryableTendersDBDelegate.findAssessmentToolByInternalName(assessment.getToolId())
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format(ERR_FMT_TOOL_NOT_FOUND, assessment.getToolId())));

    var entity = new AssessmentEntity();
    entity.setTool(tool);
    entity.setStatus("Status"); // ?
    entity.setBuyerOrganisationId(1); // ?
    entity.setTimestamps(createTimestamps(principal));

    var assessmentId = retryableTendersDBDelegate.save(entity).getId().intValue();

    // Save dimension weightings
    // TODO: needs some validation that Dimensions are valid for that Tool
    assessment.getDimensionRequirements().forEach(dr -> {
      var selection = new AssessmentDimensionWeighting();
      selection.setAssessmentId(assessmentId);
      selection.setDimensionName(dr.getName());
      selection.setWeightingPercentage(new BigDecimal(dr.getWeighting()));
      selection.setTimestamps(createTimestamps(principal));
      retryableTendersDBDelegate.save(selection);

      dr.getRequirements().forEach(r -> {

        r.get
        var as = new AssessmentSelection();
        as.setDimensionName(dr.getName());
        as.setWeightingPercentage(new BigDecimal(r.getWeighting()));
        as.setTimestamps(createTimestamps(principal));
        as.setRequirementTaxon(null)
        retryableTendersDBDelegate.save(as);
      });

    });


    return assessmentId;
  }

  /**
   *
   * @param principal
   * @return
   */
  public List<AssessmentSummary> getAssessmentsForUser(final String principal) {
    Set<AssessmentEntity> assessments =
        retryableTendersDBDelegate.findAssessmentsForUser(principal);

    return assessments.stream().map(a -> {
      var summary = new AssessmentSummary();
      summary.setAssessmentId(a.getId());
      summary.setToolId(a.getTool().getInternalName());
      return summary;
    }).collect(Collectors.toList());
  }

  /**
   *
   * @param assessmentId
   * @param principal
   * @return
   */
  public Assessment getAssessment(final Integer assessmentId, final String principal) {

    AssessmentEntity assessment = retryableTendersDBDelegate.findAssessmentById(assessmentId)
        .orElseThrow(() -> new ResourceNotFoundException(
            String.format(ERR_FMT_ASSESSMENT_NOT_FOUND, assessmentId)));

    if (assessment.getTimestamps().getCreatedBy().equals(principal)) {
      throw new AuthorisationFailureException("User is not authorised to view that Assessment");
    }

    var dimensions = assessment.getDimensionWeightings().stream().map(dw -> {
      var req = new DimensionRequirement();
      req.setName(dw.getDimensionName());
      req.setWeighting(dw.getWeightingPercentage().intValue());
      return req;
    }).collect(Collectors.toList());

    var response = new Assessment();
    response.setToolId(assessment.getTool().getInternalName());
    response.setAssesmentId(assessmentId);
    response.setDimensionRequirements(dimensions);

    return response;
  }

  /**
   * TEMP
   */
  private Timestamps createTimestamps(final String userId) {
    var ts = new Timestamps();
    ts.setCreatedAt(Instant.now());
    // ts.setUpdatedAt(Instant.now());
    ts.setCreatedBy(userId);
    // ts.setUpdatedBy(userId);
    return ts;
  }

}
