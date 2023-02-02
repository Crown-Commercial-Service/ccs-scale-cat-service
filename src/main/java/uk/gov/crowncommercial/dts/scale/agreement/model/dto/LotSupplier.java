package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

import java.util.Set;

/**
 * LotSupplier
 */
@Data
public class LotSupplier {

  private Organization organization;

  private SupplierStatus supplierStatus;

  private Set<Contact> lotContacts;
}
