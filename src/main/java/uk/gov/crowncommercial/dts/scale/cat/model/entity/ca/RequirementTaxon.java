package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "requirement_taxons")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RequirementTaxon {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "requirement_taxon_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "requirement_id")
  RequirementEntity requirement;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "assessment_taxon_id")
  AssessmentTaxon taxon;

  @Embedded
  private Timestamps timestamps;
}
