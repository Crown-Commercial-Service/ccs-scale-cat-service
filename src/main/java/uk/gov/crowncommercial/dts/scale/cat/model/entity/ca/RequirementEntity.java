package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import java.util.Set;
import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "requirements")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(exclude = "requirementTaxons")
public class RequirementEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "requirement_id")
  Integer id;

  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  @JoinColumn(name = "requirement_taxon_id")
  Set<RequirementTaxon> requirementTaxons;

  @Column(name = "group_requirement")
  private Boolean groupRequirement;
  
  @Column(name = "requirement_name")
  private String name;

  @Column(name = "requirement_descr")
  private String description;

  @Embedded
  private Timestamps timestamps;
}
