package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

/**
 * Mirror of Tenders API type {@link Question}. Simplified types where possible to avoid duplication
 * of enums, value types etc.
 */
@Value
@Builder
@Jacksonized
@Slf4j
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

    static final String KEY_VALUE = "value";
    static final String KEY_SELECTED = "select";

    String questionType;
    Boolean answered;
    Boolean mandatory;
    Boolean multiAnswer;

    @NonFinal
    List<Map<String, String>> options; // Maps to QuestionNonOCDSOptions

    /**
     * Updates the {@link #options} list (i.e. the answers provided by the buyer).
     *
     * @param updatedOptions
     */
    public void updateOptions(final List<Map<String, String>> updatedOptions) {

      var selectionQuestionTypes = Set.of("SingleSelect", "MultiSelect", "SingleSelectWithOptions",
          "MultiSelectWithOptions");

      if (selectionQuestionTypes.contains(questionType)) {
        updatedOptions.stream().forEach(uo -> {

          log.debug("updatedOption: " + uo);

          // Update the existing option's 'select' flag (user selecting / de-selecting one or more
          // options in a single or multi-select, for example
          var optionToUpdate =
              options.stream().filter(o -> Objects.equals(o.get(KEY_VALUE), uo.get(KEY_VALUE)))
                  .findFirst().orElseThrow(() -> new IllegalStateException(
                      "Could not find option '" + uo.get(KEY_VALUE) + "' to update."));

          log.debug("optionToUpdate: " + uo);

          optionToUpdate.replace(KEY_SELECTED, uo.get(KEY_SELECTED));
        });
      } else {
        if (options == null) {
          options = new ArrayList<>();
        }
        options.clear();
        options.addAll(updatedOptions);
      }
    }
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
