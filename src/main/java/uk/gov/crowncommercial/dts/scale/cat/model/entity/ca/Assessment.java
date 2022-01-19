package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

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
public class Assessment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_id")
  Integer id;

  @Column(name = "buyer_organisation_id")
  private Integer buyerOrganisationId;

  // TODO - typo
  @Column(name = "assesment_name")
  private String name;

  @Column(name = "assessment_descr")
  private String description;

  @Column(name = "status")
  private String status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_tool_id")
  AssessmentTool tool;

  @Embedded
  private Timestamps timestamps;
}
