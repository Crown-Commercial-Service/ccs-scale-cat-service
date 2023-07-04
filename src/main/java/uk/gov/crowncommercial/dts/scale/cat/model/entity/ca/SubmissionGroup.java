package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

import jakarta.persistence.*;
import java.util.List;
import java.util.Set;

/**
*
*/
@Entity
@Table(name = "submission_group")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubmissionGroup {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "submission_group_id")
  Integer id;

  @EqualsAndHashCode.Exclude
  @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JoinColumn(name = "lot_id", referencedColumnName = "submission_group_id")
  List<LotRequirementTaxon> requirementTaxonList;

  @EqualsAndHashCode.Exclude
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "assessment_tool_submission_group", joinColumns = @JoinColumn(name = "submission_group_id"),
          inverseJoinColumns = @JoinColumn(name = "assessment_tool_id"))
  Set<AssessmentTool> assessmentTools;

  @Column(name = "submission_group_desc")
  private String description;

  @Column(name = "submission_group_external_code")
  private String externalCode;

  @Embedded
  private Timestamps timestamps;
}
