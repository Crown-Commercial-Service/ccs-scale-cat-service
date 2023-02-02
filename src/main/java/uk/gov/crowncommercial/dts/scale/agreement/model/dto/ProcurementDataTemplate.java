package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProcurementDataTemplate {
    private Integer id;
    private String templateName;
    private Integer parent;
    private Boolean mandatory;
    private Object criteria;
}
