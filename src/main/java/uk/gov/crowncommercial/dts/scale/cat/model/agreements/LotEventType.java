package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import lombok.Data;

import java.util.Collection;

@Data
public class LotEventType {

  private String type;

  private String description;

  private Boolean preMarketActivity;

  private String assessmentToolId;

  private Collection<QuestionTemplate> templateGroups;
}
