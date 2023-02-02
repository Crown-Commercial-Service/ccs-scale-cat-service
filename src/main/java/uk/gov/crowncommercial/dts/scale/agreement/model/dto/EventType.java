package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

import java.util.Collection;

/**
 * EventType
 */
@Data
public class EventType {

  private String type;

  private String description;

  private Boolean preMarketActivity;

  private String assessmentToolId;

  private Boolean mandatoryEventInd;

  private Collection<QuestionTemplate> templateGroups;
}
