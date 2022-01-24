package uk.gov.crowncommercial.dts.scale.cat.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentDimensionWeighting;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentService {

  private static final String ERR_FMT_TOOL_NOT_FOUND = "Assessment Tool [%s] not found";
  private static final String ERR_FMT_ASSESSMENT_NOT_FOUND = "Assessment [%s] not found";

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

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
    assessment.getRequirements().forEach(r -> {
      var selection = new AssessmentDimensionWeighting();
      selection.setAssessmentId(assessmentId);
      selection.setDimensionName(r.getName());
      selection.setWeightingPercentage(new BigDecimal(r.getWeighting()));
      selection.setTimestamps(createTimestamps(principal));
      retryableTendersDBDelegate.save(selection);
    });

    return assessmentId;
  }

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

  public Assessment getAssessment(final Integer assessmentId, final String principal) {

    // TODO - security validation check

    AssessmentEntity assessment = retryableTendersDBDelegate.findAssessmentById(assessmentId)
        .orElseThrow(() -> new ResourceNotFoundException(
            String.format(ERR_FMT_TOOL_NOT_FOUND, assessmentId)));

    var dimensions = assessment.getDimensionWeightings().stream().map(dw -> {
      DimensionRequirement req = new DimensionRequirement();
      req.setName(dw.getDimensionName());
      req.setWeighting(dw.getWeightingPercentage().intValue());
      return req;
    }).collect(Collectors.toList());

    var response = new Assessment();
    response.setToolId(assessment.getTool().getInternalName());
    response.setAssesmentId(assessmentId);
    response.setRequirements(dimensions);

    return response;
  }

  private Timestamps createTimestamps(final String userId) {
    var ts = new Timestamps();
    ts.setCreatedAt(Instant.now());
    ts.setUpdatedAt(Instant.now());
    ts.setCreatedBy(userId);
    ts.setUpdatedBy(userId);
    return ts;
  }
}
