package uk.gov.crowncommercial.dts.scale.cat.model.dmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FrameworkContactInformation {

    @JsonProperty("digital-outcomes-and-specialists")
    public FrameworkContact digitalOutcomesAndSpecialists;

    @JsonProperty("g-cloud")
    public FrameworkContact gCloud;
}