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
public class AssessmentTools {

  @Id
  @Column(name = "assessment_tool_id")
  Integer id;

  // Unique key used as reference in Agreements Service
  @Column(name = "internal_tool_name")
  private String internalName;

  @Column(name = "assessment_tool_name")
  private String name;

  @Column(name = "assessment_tool_descr")
  private String description;

  @Embedded
  private Timestamps timestamps;
}
