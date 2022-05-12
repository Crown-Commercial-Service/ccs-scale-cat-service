package uk.gov.crowncommercial.dts.scale.cat.model.entity;

/**
 * DocGen enum to identify the target component 'type' in the ODT for value replacement
 */
public enum TargetType {

  // Single value types
  SIMPLE, DATETIME, DURATION,

  // Multi-value types
  LIST, TABLE;

}
