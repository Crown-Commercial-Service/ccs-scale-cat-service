package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

/**
 *
 */
@Value
@Builder
@Jacksonized
@Data
public class LotSupplier {

  Organization organization;

  private Set<Contact> lotContacts;
}
