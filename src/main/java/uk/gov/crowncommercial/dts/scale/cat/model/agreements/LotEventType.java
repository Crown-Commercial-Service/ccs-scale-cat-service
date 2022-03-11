package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import lombok.Data;

@Data
public class LotEventType {

  private String type;

  private String description;

  private Boolean preMarketActivity;

  private String assessmentToolId;
}
