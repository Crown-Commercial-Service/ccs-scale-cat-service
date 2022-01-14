package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "cap_supplier_submissions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapSupplierSubmission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "cap_supplier_submission_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lot_product_taxon_id")
  CapLotProductTaxon lotProductTaxon;

  // ??...
  @Column(name = "supplier_id")
  private Integer supplierId;

  @Column(name = "submission_type_code")
  private String code;

  @Column(name = "submission_reference")
  private String reference;

  @Column(name = "submission_value")
  private Integer value;
}
