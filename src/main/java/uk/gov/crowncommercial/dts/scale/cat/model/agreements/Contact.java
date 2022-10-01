package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

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
