package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import java.math.BigDecimal;
import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
// TODO - should be 'assessment_results'?
@Table(name = "assessment_result")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_result_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_id")
  Assessment assessment;

  @Column(name = "supplier_organisation_id")
  private Integer supplierOrganisationId;

  @Column(name = "assessment_result_value")
  private BigDecimal assessmentResultValue;

  @Column(name = "assessment_selection_result_reference")
  private String assessmentSelectionResultReference;

  @Embedded
  private Timestamps timestamps;
}
