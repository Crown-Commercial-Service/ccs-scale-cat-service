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
@Table(name = "assessments")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_id")
  Integer id;

  @Column(name = "buyer_organisation_id")
  private String buyerOrganisationId;

  @Column(name = "status")
  @Enumerated(EnumType.STRING)
  private AssessmentStatus status;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "assessment_tool_id")
  AssessmentTool tool;

  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  @JoinColumn(name = "assessment_id")
  Set<AssessmentDimensionWeighting> dimensionWeightings;

  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  @JoinColumn(name = "assessment_id")
  Set<AssessmentSelection> assessmentSelections;

  @Embedded
  private Timestamps timestamps;
}
