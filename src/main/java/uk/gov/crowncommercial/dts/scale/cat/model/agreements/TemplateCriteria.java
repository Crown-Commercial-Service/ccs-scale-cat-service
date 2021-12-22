package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import java.util.Set;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EvalCriteria;

/**
 * Top level model for template data held in AS. Broadly aligns with {@link EvalCriteria}
 */
@Value
@Builder
@Jacksonized
public class TemplateCriteria {

  String id;
  String title;
  String description;
  Party source;
  Party relatesTo;
  String relateItems;
  Set<RequirementGroup> requirementGroups;

}
