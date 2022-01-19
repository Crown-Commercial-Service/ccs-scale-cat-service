package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirror type of {@link QuestionType}
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
  TEXT,

  @JsonProperty("Monetary")
  MONETARY,

  @JsonProperty("Duration")
  DURATION,

  @JsonProperty("Date")
  DATE,

  @JsonProperty("DateTime")
  DATETIME,

  @JsonProperty("Integer")
  INTEGER,

  @JsonProperty("ReadMe")
  README,

  @JsonProperty("Percentage")
  PERCENTAGE;
}
