package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 *
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExportRfxResponse {

  RfxSetting rfxSetting;
  EmailRecipientList emailRecipientList;
  SuppliersList suppliersList;
}
