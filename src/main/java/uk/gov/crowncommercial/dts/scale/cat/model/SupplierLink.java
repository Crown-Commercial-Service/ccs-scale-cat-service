package uk.gov.crowncommercial.dts.scale.cat.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierLink {
    private SchemeType ppgId;
    private SchemeType casId;
    private Integer bravoId;
    private String cohNumber;
    private String dunsNumber;
    private String vatNumber;
    private String nhsNumber;
}
