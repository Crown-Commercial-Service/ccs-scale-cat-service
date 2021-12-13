package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionNonOCDS.QuestionTypeEnum;

/**
 * Mirror type of {@link QuestionTypeEnum}
 */
public enum QuestionType {
  @JsonProperty("Value")
  VALUE,

  @JsonProperty("Range")
  RANGE,

  @JsonProperty("SingleSelect")
  SINGLESELECT,

  @JsonProperty("MultiSelect")
  MULTISELECT,

  @JsonProperty("SingleSelectWithOptions")
  SINGLESELECTWITHOPTIONS,

  @JsonProperty("MultiSelectWithOptions")
  MULTISELECTWITHOPTIONS,

  @JsonProperty("Document")
  DOCUMENT,

  @JsonProperty("DocumentWithTemplate")
  DOCUMENTWITHTEMPLATE,

  @JsonProperty("Address")
  ADDRESS,

  @JsonProperty("KeyValuePair")
  KEYVALUEPAIR,

  @JsonProperty("Text")
  TEXT;
}
