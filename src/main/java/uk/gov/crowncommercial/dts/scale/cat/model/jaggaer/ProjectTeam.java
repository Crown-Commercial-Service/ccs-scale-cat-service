package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.Set;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class ProjectTeam {

  Set<User> user;

}
