package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "submission_types")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubmissionType {

  @Id
  @Column(name = "submission_type_code")
  private String code;

  @Column(name = "submission_type_name")
  private String name;

  @Column(name = "submission_type_descr")
  private String description;

  @Embedded
  private Timestamps timestamps;
}
