package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class MobileTelephoneNumber {
    String countryCode;
    String areaCode;
    String number;
}
