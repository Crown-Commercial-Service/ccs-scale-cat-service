package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 *
 */
@Data
public class ReturnSubUser {

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class SubUser {

    String name;
    String surname;
    String email;
    String login;
    String userId;

  }

  @JsonProperty("subUser")
  private Set<SubUser> subUsers;

}
