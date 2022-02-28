package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

import java.util.List;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Jacksonized
@Builder
public class RetryConfiguartion {
  List<String> previousRequestIds;
  int noOfTimesRetry;
  long retryCoolOffInterval;
}