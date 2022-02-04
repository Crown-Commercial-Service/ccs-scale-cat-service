package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Type of relationship to dependent field   * GreaterThan (Dependent value greater than dependencyValue)    * EqualTo (Dependent value equal to the dependencyValue)   * LessThan (Dependent value less than the dependency value)   * Inside (Dependent value inside a range or period)   * Outside (Dependent value outside a range or period)   * NotNull (Any Dependent value has been provided) 
 */
public enum DependencyType {
  
  GREATERTHAN("GreaterThan"),
  
  EQUALTO("EqualTo"),
  
  LESSTHAN("LessThan"),
  
  INSIDE("Inside"),
  
  OUTSIDE("Outside"),
  
  NOTNULL("NotNull");

  private String value;

  DependencyType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static DependencyType fromValue(String value) {
    for (DependencyType b : DependencyType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

