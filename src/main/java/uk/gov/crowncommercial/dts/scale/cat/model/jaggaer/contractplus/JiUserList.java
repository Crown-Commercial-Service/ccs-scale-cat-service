package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

@Value
@Builder
@Jacksonized
public class JiUserList {

    @Value
    @Builder
    @Jacksonized
    public static class JiUser {

        String userName;
        String firstName;
        String lastName;
        String userStatus;
        String email;
        TelephoneNumber telephoneNumber;
        MobileTelephoneNumber mobileTelephoneNumber;
        International international;
        String authorizationMethod;
        BusinessUnit businessUnit;
        Department department;
        String position;
        RoleList roleList;
    }

    @JsonProperty("jiuser")
    Set<JiUser> jiUser;

}