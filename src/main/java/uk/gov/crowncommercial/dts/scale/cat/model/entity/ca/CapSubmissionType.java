package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "cap_submission_types")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapSubmissionType {

  @Id
  @Column(name = "submission_type_name")
  private String name;

  @Column(name = "submission_type_code")
  private String code;
}
