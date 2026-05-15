package uk.gov.crowncommercial.dts.scale.cat.model.dmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupplierDetail {

  public Supplier suppliers;
}
