package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "assessment_taxon_dimensions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentTaxonDimension {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_taxon_dimension_id")
  Integer id;

  @Column(name = "assessment_taxon_id")
  private Integer assessmentTaxonId;

  @Column(name = "dimension_id")
  private Integer dimensionId;

  @Embedded
  private Timestamps timestamps;
}
