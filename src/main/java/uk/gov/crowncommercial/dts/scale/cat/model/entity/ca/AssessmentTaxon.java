package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "assessment_taxons")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentTaxon {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_taxon_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_tool_id")
  AssessmentTool tool;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_tool_id")
  AssessmentTool parentTool;

  @Column(name = "assessment_taxon_name")
  private String name;

  @Column(name = "permalink")
  private String link;

  @Column(name = "assessment_taxon_descr")
  private String description;

  @Embedded
  private Timestamps timestamps;
}
