package uk.gov.crowncommercial.dts.scale.cat.csvreader;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class SupplierContactModel {
    @NotNull
    private String entityId;

    private String registryCode = "US-DUNS";
    @NotNull
    private String supplierName;
    @NotNull
    private String agreement;
    @NotNull
    private Date effectiveFrom;

    private String lot;
    private String contactName;
    private String emailAddress;
    private String phoneNumber;
    private String website;
    private String postalAddress;
    private String sme;
    private Boolean isPrimary;


    // Used for internal service;
    private Integer lorId;
}
