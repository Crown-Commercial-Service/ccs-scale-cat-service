package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.Set;
import lombok.Data;

@Data
public class GetCompanyDataResponse {

  String returnCode;
  String returnMessage;
  Integer totRecords;
  Integer returnedRecords;
  Set<CompanyInfo> returnCompanyData;

}
