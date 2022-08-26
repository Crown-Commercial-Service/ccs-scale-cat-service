package uk.gov.crowncommercial.dts.scale.cat.model.assessment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SupplierScore {
    @JsonProperty("name")
    private String name;
    @JsonProperty("identifier")
    private String identifier;

    @JsonProperty("streetAddress")
    private String streetAddress;
    @JsonProperty("locality")
    private String locality;
    @JsonProperty("region")
    private String region;
    @JsonProperty("postalCode")
    private String postalCode;
    @JsonProperty("countryName")
    private String countryName;
    @JsonProperty("contactName")
    private String contactName;
    @JsonProperty("contactEmail")
    private String contactEmail;
    @JsonProperty("contactTelephone")
    private String contactTelephone;
    @JsonProperty("contactFaxNumber")
    private String contactFaxNumber;
    @JsonProperty("contactUrl")
    private String contactUrl;

    @JsonProperty("score")
    private Double score;
}
