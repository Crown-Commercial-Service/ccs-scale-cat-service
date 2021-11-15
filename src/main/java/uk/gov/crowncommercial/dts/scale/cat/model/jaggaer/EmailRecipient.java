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
public class EmailRecipient {

  User user;
  String email;
  String division;
  String profile;
  Integer objectVisibility;
  Integer divisionId;
  Integer profileId;
}
