package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ProjectOwner {

  String id;
  
  String login; // Added by RoweIT EI-74 /salesforce endpoint

}
