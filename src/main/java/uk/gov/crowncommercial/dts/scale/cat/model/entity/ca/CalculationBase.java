package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 *
 */
@Entity
@Table(name = "calculation_base")
@Immutable
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CalculationBase {

  @Id
  Long id;

  @Column(name = "assessment_id")
  Integer assessmentId;

  @Column(name = "supplier_id")
  String supplierId;

  @Column(name = "assessment_tool_name")
  String assessmentToolName;

  @Column(name = "submission_type_name")
  String submissionTypeName;

  @Column(name = "dimension_name")
  String dimensionName;

  @Column(name = "dimension_id")
  Integer dimensionId;

  @Column(name = "requirement_name")
  String requirementName;

  @Column(name = "submission_reference")
  String submissionReference;

  @Column(name = "submission_value")
  String submissionValue;

  @Column(name = "adw_weighting_pct")
  BigDecimal assessmentDimensionWeightPercentage;

  @Column(name = "asel_weighting_pct")
  BigDecimal assessmentSelectionWeightPercentage;

  @Column(name = "dimension_divisor")
  Integer dimensionDivisor;

}
