package uk.gov.crowncommercial.dts.scale.cat.csvreader;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class SupplierModel {
    @NotNull
    private String entityId;

    private String registryCode = "US-DUNS";

    private String houseNumber;
    @NotNull
    private String supplierName;

    private String legalName;
    private String tradingName;

    private String bravoId;
    private String jaggaerSupplierName, jaggaerExtCode;

    private Double similarity;

    private String mappingType;
    private String mappingId;
    private String ciiOrgName;
    private String ciiCoH;
    private String fuzzyMatch;

    private String ciiMatch;
    private String emailAddress;

    private String postalAddress;

    public void setMapping(String type, String id){
        this.mappingType = type;
        this.mappingId = id;
    }
}
