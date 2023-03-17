package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdditionalInfo {

  String name;
  String label;
  String labelLocale;
  @JsonProperty("values")
  AdditionalInfoValues values;
  
  // Added by RoweIT for Tenders API integration
  String type;
  Integer visibleToSupplier;
  
}
