package uk.gov.crowncommercial.dts.scale.cat.model.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Request model for saving exit award data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExitAwardRequest {

    @NotBlank(message = "Supplier Awarded is mandatory")
    @JsonProperty("supplierAwarded")
    private String supplierAwarded;

    @NotNull(message = "Contract Start Date is mandatory")
    @JsonProperty("contractStartDate")
    private OffsetDateTime contractStartDate;

    @NotBlank(message = "Contract Value is mandatory")
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$", message = "Contract Value must be a valid number")
    @JsonProperty("contractValue")
    private String contractValue;

    @Pattern(regexp = "^(https?://)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([/\\w \\.-]*)*/?$",
             message = "Award URL must be a valid URL format")
    @JsonProperty("awardUrl")
    private String awardUrl; // Optional
}
