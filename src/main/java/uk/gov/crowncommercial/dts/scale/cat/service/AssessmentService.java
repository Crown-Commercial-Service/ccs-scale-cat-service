package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Service
@RequiredArgsConstructor
public class AssessmentService {

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  public Integer createAssessment(final Assessment assessment) {

    return 0;
  }

  public List<AssessmentSummary> getAssessmentsForUser(final String principal) {

    Set<AssessmentEntity> assessments =
        retryableTendersDBDelegate.findAssessmentsForUser(principal);

    return assessments.stream().map(a -> {
      AssessmentSummary summary = new AssessmentSummary();
      summary.setAssessmentId(a.getId());
      summary.setToolId(a.getTool().getId());
      return summary;
    }).collect(Collectors.toList());
  }
}
