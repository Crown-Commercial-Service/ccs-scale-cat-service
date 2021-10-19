package uk.gov.crowncommercial.dts.scale.cat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EventType
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventType {

  private String type;

  private String description;
}
