package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus;

import lombok.Value;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Value
@SuperBuilder
@Jacksonized
public class TelephoneNumber {
    String countryCode;
    String areaCode;
    String number;
}