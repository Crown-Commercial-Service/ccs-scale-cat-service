package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

import jakarta.persistence.*;

@Entity
@Table(name = "supplier_link")
@Data
public class SupplierLinkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_link_id")
    private Integer supplierLinkId;
    @Column(name = "ppg_id")
    private String ppgId;
    @Column(name = "cas_id")
    private String casId;
    @Column(name = "bravo_id")
    private Integer bravoId;
    @Column(name = "coh_number")
    private String cohNumber;
    @Column(name = "duns_number")
    private String dunsNumber;
    @Column(name = "vat_number")
    private String vatNumber;
    @Column(name = "nhs_number")
    private String nhsNumber;

    @Embedded
    private Timestamps timestamps;
}
