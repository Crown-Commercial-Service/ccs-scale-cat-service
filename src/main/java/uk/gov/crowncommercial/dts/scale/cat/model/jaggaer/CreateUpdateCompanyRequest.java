package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;

/**
 *
 */
@Value
@Builder
public class CreateUpdateCompanyRequest {

  private CreateUpdateCompany company;
  private SubUsers subUsers;

}
