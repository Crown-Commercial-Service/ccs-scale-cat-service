package uk.gov.crowncommercial.dts.scale.cat.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ApplicationUtils {

  public String formatAccessToken(final String accessToken) {
    return "Bearer " + accessToken;
  }
}
