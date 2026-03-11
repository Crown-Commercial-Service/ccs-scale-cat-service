package uk.gov.crowncommercial.dts.scale.cat.model.dmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Supplier {

  public Long id;
  public String name;
  public String organisationSize;
  public String registeredName;
  public String registrationCountry;
  public String tradingStatus;
  public String companiesHouseNumber;
  public String description;
  public String dunsNumber;
  public boolean companyDetailsConfirmed;
  public Map<String, String> links;

  @JsonProperty("service_counts")
  public Map<String, String> serviceCounts;

  public List<ContactInformation> contactInformation = new ArrayList<>();
}
