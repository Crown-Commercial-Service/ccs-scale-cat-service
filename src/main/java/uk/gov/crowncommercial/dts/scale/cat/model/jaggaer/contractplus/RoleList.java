package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

public class RoleList {

    @Value
    @Builder
    @Jacksonized
    public static class Role {
        String roleName;
    }
    @JsonProperty("role")
    Set<Role> role;

}