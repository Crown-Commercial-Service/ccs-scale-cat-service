package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Data;

@Data
public class CompanyInfo {

  @Data
  public static class CompanyInfoDetail {

    String bravoId;
    String companyName;
    String userName;
    String userEmail;
    String userId;
  }

  CompanyInfoDetail returnCompanyInfo;
}
