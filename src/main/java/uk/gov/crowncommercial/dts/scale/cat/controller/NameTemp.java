package uk.gov.crowncommercial.dts.scale.cat.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NameTemp {

  @JsonProperty("name")
  private String name;

}
