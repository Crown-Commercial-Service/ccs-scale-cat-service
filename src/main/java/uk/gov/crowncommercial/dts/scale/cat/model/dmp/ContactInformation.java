package uk.gov.crowncommercial.dts.scale.cat.model.dmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactInformation {

  public String id;
  public String address1;
  public String city;
  public String contactName;
  public String email;
  public boolean personalDataRemoved;
  public String phoneNumber;
  public String postcode;
  public Map<String, String> links;

  public String getFullAddress() {
    return String.format("%s, %s, %s", address1, city, postcode);
  }
}
