package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "dimension_submission_types")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DimensionSubmissionType {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "dimension_submission_type_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "submission_type_code")
  SubmissionType submissionType;

  @EqualsAndHashCode.Exclude
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "dimension_id")
  DimensionEntity dimension;

  @Column(name = "selection_type")
  private String selectionType;

  @Column (name ="submission_mandatory")
  @Builder.Default
  private Boolean mandatory = true;

  @Embedded
  private Timestamps timestamps;
}
