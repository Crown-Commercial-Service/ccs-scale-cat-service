package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * List or register from which an organization identifier is taken
 */
public enum Scheme {

  @JsonProperty("GB-COH")
  GB_COH,

  @JsonProperty("GB-MPR")
  GB_MPR,

  @JsonProperty("GB-NIC")
  GB_NIC,

  @JsonProperty("GB-CHC")
  GB_CHC,

  @JsonProperty("GB-SC")
  GB_SC,

  @JsonProperty("GB-WALEDU")
  GB_WALEDU,

  @JsonProperty("GB-SCOTEDU")
  GB_SCOTEDU,

  @JsonProperty("GB-GOR")
  GB_GOR,

  @JsonProperty("GB-LANI")
  GB_LANI,

  @JsonProperty("GB-NHS")
  GB_NHS,

  @JsonProperty("GB-SRS")
  GB_SRS,

  @JsonProperty("US-DUNS")
  US_DUNS,

}
