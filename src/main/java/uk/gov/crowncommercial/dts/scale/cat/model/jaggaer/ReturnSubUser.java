package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Data
public class ReturnSubUser {

  @Value
  @Builder
  @Jacksonized
  public static class SubUser {

    String name;
    String surname;
    String email;
    String login;
    String userId;
    String phoneNumber;
    String mobilePhoneNumber;

  }

  @JsonProperty("subUser")
  private Set<SubUser> subUsers;

}
