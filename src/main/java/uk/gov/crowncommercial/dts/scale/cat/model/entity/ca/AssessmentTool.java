package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "assessment_tools")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentTool {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_tool_id")
  Integer id;

  @Column(name = "external_assessment_tool_id")
  private String externalToolId;

  @Column(name = "assessment_tool_name")
  private String name;

  @Column(name = "assessment_tool_descr")
  private String description;

  // @EqualsAndHashCode.Exclude
  // @ManyToMany(fetch = FetchType.EAGER)
  // @JoinTable(name = "assessment_submission_types",
  // joinColumns = @JoinColumn(name = "assessment_tool_id"),
  // inverseJoinColumns = @JoinColumn(name = "submission_type_code"))
  // Set<SubmissionType> submissionTypes;

  @Embedded
  private Timestamps timestamps;
}
