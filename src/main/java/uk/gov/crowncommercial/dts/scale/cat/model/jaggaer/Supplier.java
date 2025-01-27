package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Supplier {

  CompanyData companyData;
  Integer invited;
  String status;
  Integer statusCode;
  Integer supplierStatus;
  String id;

}
