package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import java.util.Set;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Question;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class RequirementGroup {

  String id;
  String description;
  String prompt;
  String task;
  Boolean mandatory;
  Set<Question> requirements; // Should match Tenders API Question type
}
