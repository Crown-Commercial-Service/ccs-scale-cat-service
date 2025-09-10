package uk.gov.crowncommercial.dts.scale.cat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDunsUpdate {
    private String currentDunsNumber;
    private String replacementDunsNumber;
}