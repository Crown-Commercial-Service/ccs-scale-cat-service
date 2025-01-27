package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
@lombok.Value
@Builder
@Jacksonized
public class Values {
    List<Value> value;
}
