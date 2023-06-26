package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "lot_requirement_taxons")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LotRequirementTaxon {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "lot_requirement_taxon_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requirement_taxon_id")
  RequirementTaxon productTaxon;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submission_group_id")
  SubmissionGroup submissionGroup;

  @Embedded
  private Timestamps timestamps;
}
