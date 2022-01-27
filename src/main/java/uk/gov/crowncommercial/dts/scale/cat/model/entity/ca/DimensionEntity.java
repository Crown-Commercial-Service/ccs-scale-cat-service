package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import java.math.BigDecimal;
import java.util.Set;
import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "dimensions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DimensionEntity {

  @Id
  @Column(name = "dimension_name")
  private String name;

  @EqualsAndHashCode.Exclude
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "assessment_taxon_dimensions",
      joinColumns = @JoinColumn(name = "dimension_name"),
      inverseJoinColumns = @JoinColumn(name = "assessment_taxon_id"))
  Set<AssessmentTaxon> assessmentTaxons;


  @Column(name = "dimension_descr")
  private String description;

  @Column(name = "min_weighting_pct")
  private BigDecimal minWeightingPercentage;

  @Column(name = "max_weighting_pct")
  private BigDecimal maxWeightingPercentage;

  @Column(name = "allow_multiple_selection_ind")
  private Boolean allowMultipleSelection;

  @Column(name = "min_allowed_value")
  private BigDecimal minAllowedValue;

  @Column(name = "max_allowed_value")
  private BigDecimal maxAllowedValue;

  @Embedded
  private Timestamps timestamps;

}
