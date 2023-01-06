package uk.gov.crowncommercial.dts.scale.cat.csvreader;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Date;

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
    private String jaggaerSupplierName;

    private Double similarity;
}
