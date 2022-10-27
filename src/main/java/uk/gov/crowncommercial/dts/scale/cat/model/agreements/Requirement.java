package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.validation.ValidationException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TableDefinition;

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
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class NonOCDS {

    String questionType;
    @NonFinal
    Boolean answered;
    Boolean mandatory;
    Boolean multiAnswer;
    Integer order;
    Integer length;
    @JsonProperty("inheritance")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NonFinal
    DataTemplateInheritanceType inheritance;
    String inheritsFrom;
    Dependency dependency;

    @NonFinal
    List<Option> options; // Maps to QuestionNonOCDSOptions

    public void setInheritance(DataTemplateInheritanceType inheritance){
      this.inheritance = inheritance;
    }

    public void setAnswered(Boolean answered){
      //TODO - Whether this should be automatically populated based on the options;
      this.answered = answered;
    }

    /**
     * Updates the {@link #options} list (i.e. the answers provided by the buyer).
     *
     * @param updatedOptions
     */
    public void updateOptions(final List<Option> updatedOptions) {

      var selectionQuestionTypes = Set.of("SingleSelect", "MultiSelect", "SingleSelectWithOptions",
          "MultiSelectWithOptions");

      if (selectionQuestionTypes.contains(questionType)) {

        // Deselect all options before applying the selection
        // Caters for scenario where only the selected options are supplied
        options.stream().forEach(o -> o.setSelect(Boolean.FALSE));

        updatedOptions.stream().forEach(updateOption -> {

          log.debug("updatedOption: " + updateOption);

          // Update the existing updateOption's 'select' flag (user selecting / de-selecting one or
          // more options in a single or multi-select, for example
          var optionToUpdate = options.stream()
              .filter(option -> Objects.equals(option.getValue(), updateOption.getValue()))
              .findFirst().orElseThrow(() -> new ValidationException(
                  "Could not find updateOption '" + updateOption.getValue() + "' to " + "update."));

          log.debug("optionToUpdate: " + updateOption);
          optionToUpdate.setSelect(updateOption.getSelect());
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

  @Data
  @Builder
  @Jacksonized
  public static class Option {

    String value;
    Boolean select;
    String text;
    TableDefinition tableDefinition;
  }
}
