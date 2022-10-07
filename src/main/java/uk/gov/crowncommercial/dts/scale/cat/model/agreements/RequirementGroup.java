package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class RequirementGroup {

    @Value
    @Builder
    @Jacksonized
    public static class NonOCDS {

        String prompt;
        String task;
        Boolean mandatory;
        Integer order;
        @NonFinal
        @JsonProperty("inheritance")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private DataTemplateInheritanceType inheritance;

        public void setInheritance(DataTemplateInheritanceType inheritance) {
            this.inheritance = inheritance;
        }
    }

    @Value
    @Builder
    @Jacksonized
    public static class OCDS {

        String id;
        String description;
        Set<Requirement> requirements; // Should match Tenders API Question type
    }

    NonOCDS nonOCDS;

    @JsonProperty("OCDS")
    OCDS ocds;

}
