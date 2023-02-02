package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Contact
 */
@Data
public class Contact {

  @JsonProperty("contact")
  ContactPoint contactPoint;

  String contactId;

  String contactReason;

}
