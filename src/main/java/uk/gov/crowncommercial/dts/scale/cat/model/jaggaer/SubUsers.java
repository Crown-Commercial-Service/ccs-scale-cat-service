package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class SubUsers {

  @Value
  @Builder
  @Jacksonized
  public static class SubUser {

    String name;
    String surName;
    String email;
    String login;
    String userId;
    String phoneNumber;
    String mobilePhoneNumber;
    String division;
    String businessUnit;
    String rightsProfile;
    String language;
    SSOCodeData ssoCodeData;
  }

  private OperationCode operationCode;
  private String sendEMail;

  @JsonProperty("subUser")
  private Set<SubUser> subUsers;

}
