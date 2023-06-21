package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;

import javax.validation.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class TimelineDependency {
    @Value
    @Builder
    @Jacksonized
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NonOCDS {
        @NonFinal
        Boolean answered;
        @NonFinal
        Dependency dependency;
        @NonFinal
        List<Requirement.Option> options;

        @NonFinal
        Conditional conditional;

        public void setAnswered(Boolean answered){
            this.answered = answered;
        }
        public void setDependency(Dependency dependency){
            this.dependency = dependency;
        }

        public void updateOptions(final List<Requirement.Option> updatedOptions) {


                if(options == null) {
                    options = new ArrayList<>();
                    options.addAll(updatedOptions);
                }else {
                    options = Requirement.NonOCDS.populateOptions(updatedOptions, options);
                }
        }

    }

    @Value
    @Builder
    @Jacksonized
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OCDS {
        @NonFinal
        String title;
        @NonFinal
        String description;
        @NonFinal
        Set<Requirement> requirements;

        public void setTitle(String title){
            this.title = title;
        }
        public void setDescription(String description){
            this.description = description;
        }

    }

    NonOCDS nonOCDS;

    @JsonProperty("OCDS")
    OCDS ocds;
}
