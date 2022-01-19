package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.List;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentSummary;

@Service
@RequiredArgsConstructor
public class AssessmentService {

  public Integer createAssessment(final Assessment assessment) {

    return 0;
  }

  public List<AssessmentSummary> getAssessmentsForUser(final String principal) {

    return null;
  }
}
