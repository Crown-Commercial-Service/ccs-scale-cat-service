package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ProjectListResponse {
  private Integer returnCode;

  private String returnMessage;

  private Integer totRecords;

  private Integer returnedRecords;

  private Integer startAt;

  private ProjectList projectList;

}

