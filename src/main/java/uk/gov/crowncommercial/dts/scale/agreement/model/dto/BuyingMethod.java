package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Buying Methods.
 */
@RequiredArgsConstructor
public enum BuyingMethod {

  DIRECT_AWARD("DirectAward"),

  FURTHER_COMPETITION("FurtherCompetition"),

  MARKETPLACE("Marketplace"),

  E_AUCTION("EAuction");

  private final String name;

  @JsonValue
  public String getName() {
    return name;
  }

  public static BuyingMethod fromName(String name) {
    for (BuyingMethod buyingMethod : values()) {
      if (buyingMethod.name.equalsIgnoreCase(name)) {
        return buyingMethod;
      }
    }
    return null;
  }

  public static Collection<String> names() {
    return Arrays.stream(values()).map(BuyingMethod::getName).collect(Collectors.toList());
  }

}
