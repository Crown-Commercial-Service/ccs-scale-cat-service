package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "supplier_submissions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupplierSubmission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "supplier_submission_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lot_requirement_taxon_id")
  LotRequirementTaxon lotRequirementTaxon;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dimension_submission_type_id")
  DimensionSubmissionType dimensionSubmissionType;

  @Column(name = "submission_reference")
  private String submissionReference;

  @Column(name = "submission_value")
  private Integer submissionValue;

  @Embedded
  private Timestamps timestamps;
}
