package uk.gov.crowncommercial.dts.scale.cat.model.dmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FrameworkContact {

    public String contactName;
    public String description;
    public String email;
    public String pendingDescription;
    public String phoneNumber;
}