package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class CompanyInfo {

  String bravoId;
  String companyName;
  String userAlias;
  String fiscalCode;
  String dAndBCode;
  String extCode;
  String extUniqueCode;
  CompanyType type;

  String bizEmail;
  String bizPhone;
  String bizFax;
  String webSite;

  String address;
  String zip;
  String city;
  String province;
  String isoCountry;

  String userSurName;
  String userName;
  String userEmail;
  String userPhone;
  String userFax;
  String userMobPhone;

  String userExtCode;
  String userBusinessUnitCode;
  String userDivisionCode;

  SSOCodeData ssoCodeData;

}
