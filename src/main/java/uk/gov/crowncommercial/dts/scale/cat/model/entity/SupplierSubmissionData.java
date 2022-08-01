package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;

/**
 *
 */
@Entity
@Table(name = "supplier_submission_data")
@Immutable
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupplierSubmissionData {

    @Id
    @Column(name = "supplier_submission_id")
    String id;

    @Column(name = "supplier_id")
    String supplierId;

    @Column(name = "lot_id")
    String lotId;

    @Column(name = "assessment_tool_id")
    Integer toolId;

    @Column(name = "external_assessment_tool_id")
    String extToolId;

    @Column(name = "assessment_tool_name")
    String toolName;

    @Column(name = "submission_type_name")
    String submissionTypeName;

    @Column(name = "dimension_name")
    String dimensionName;

    @Column(name = "dimension_id")
    Integer dimensionId;

    @Column(name = "requirement_name")
    String requirementName;

    @Column(name = "submission_value")
    String submissionValue;

}
