package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Service
@RequiredArgsConstructor
public class AssessmentService {

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  public Integer createAssessment(final Assessment assessment, final String principal) {

    AssessmentTool tool =
        retryableTendersDBDelegate.findAssessmentToolById(assessment.getToolId()).get();
    AssessmentEntity entity = new AssessmentEntity();

    // TODO - WIP
    entity.setTool(tool);
    entity.setName(assessment.getRequirements().get(0).getName()); // ??
    entity.setDescription("Description");
    entity.setStatus("Status"); // ?
    entity.setBuyerOrganisationId(123);
    entity.setTimestamps(createTimestamps(principal));

    return retryableTendersDBDelegate.save(entity).getId().intValue();
  }

  public List<AssessmentSummary> getAssessmentsForUser(final String principal) {

    Set<AssessmentEntity> assessments =
        retryableTendersDBDelegate.findAssessmentsForUser(principal);

    return assessments.stream().map(a -> {
      AssessmentSummary summary = new AssessmentSummary();
      summary.setAssessmentId(a.getId().intValue());
      summary.setToolId(a.getTool().getId());
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
