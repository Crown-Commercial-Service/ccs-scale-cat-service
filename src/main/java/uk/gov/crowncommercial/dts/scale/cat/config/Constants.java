package uk.gov.crowncommercial.dts.scale.cat.config;

import lombok.experimental.UtilityClass;

/**
 * Global constant values
 */
@UtilityClass
public class Constants {

  // Controller response related
  public static final String OK = "OK";

  // Security related
  public static final String JWT_CLAIM_SUBJECT = "sub";
  public static final String ERR_MSG_UNAUTHORISED = "Missing, expired or invalid access token";
  public static final String ERR_MSG_FORBIDDEN = "Access to the requested resource is forbidden";
}
