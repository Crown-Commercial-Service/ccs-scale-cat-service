package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyUpdateRequest {
        private String companyName;
        private String bizEmail;
        private String bizPhone;
        private String website;
        private String userEmail;
        private String extUniqueCode;
        private String extCode;
        private String address;
        private String zip;
        private String city;
        private String province;
        private String isoCountry;
}

