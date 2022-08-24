package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.*;

@Entity
@Table(name = "gcloud_assessments")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GCloudAssessmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assessment_id")
    Integer id;

    @Column(name = "assessment_name")
    private String assessmentName;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private AssessmentStatusEntity status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "external_tool_id")
    AssessmentTool tool;

    @Column(name = "dimension_reqs")
    private String dimensionRequirements;
}
