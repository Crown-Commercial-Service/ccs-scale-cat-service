package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Conditional {
  String dependentOnID;
  DependencyType dependencyType;
  String dependencyValue;
}