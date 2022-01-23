package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Service
@RequiredArgsConstructor
public class AssessmentService {

  private static final String ERR_FMT_TOOL_NOT_FOUND = "Assessment Tool [%s] not found";

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  public Integer createAssessment(final Assessment assessment, final String principal) {

    // TODO - can we change 'internalName' to internalId/externalId
    AssessmentTool tool =
        retryableTendersDBDelegate.findAssessmentToolByInternalName(assessment.getToolId())
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format(ERR_FMT_TOOL_NOT_FOUND, assessment.getToolId())));

    var entity = new AssessmentEntity();
    entity.setTool(tool);
    entity.setStatus("Status"); // ?
    entity.setBuyerOrganisationId(1); // ?
    entity.setTimestamps(createTimestamps(principal));

    // TODO - save dimensions

    return retryableTendersDBDelegate.save(entity).getId().intValue();
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

  private Timestamps createTimestamps(final String userId) {
    var ts = new Timestamps();
    ts.setCreatedAt(Instant.now());
    ts.setUpdatedAt(Instant.now());
    ts.setCreatedBy(userId);
    ts.setUpdatedBy(userId);
    return ts;
  }
}
