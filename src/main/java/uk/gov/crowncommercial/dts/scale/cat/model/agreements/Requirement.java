package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Mirror of Tenders API type {@link Question}. Simplified types where possible to avoid duplication
 * of enums, value types etc.
 */
@Value
@Builder
@Jacksonized
public class Requirement {

  @Value
  @Builder
  @Jacksonized
  public static class Period {

    OffsetDateTime startDate;
    OffsetDateTime endDate;
    OffsetDateTime maxExtentDate;
    Integer durationInDays;
  }

  @Value
  @Builder
  @Jacksonized
  public static class NonOCDS {

    String questionType;
    Boolean answered;
    Boolean mandatory;
    Boolean multiAnswer;
    List<Map<String, String>> options; // Maps to QuestionNonOCDSOptions
  }

  @Value
  @Builder
  @Jacksonized
  public static class OCDS {

    String id;
    String title;
    String description;
    String pattern;
    String dataType;
    BigDecimal expectedValue;
    BigDecimal minValue;
    BigDecimal maxValue;
    Period period;
  }

  NonOCDS nonOCDS;

  @JsonProperty("OCDS")
  OCDS ocds;

}
