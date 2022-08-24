package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.repo.GCloudAssessmentResult;

import javax.persistence.*;
import java.util.Set;

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
    @JoinColumn(name = "assessment_tool_id")
    AssessmentTool tool;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "assessment_id")
    Set<GCloudAssessmentResult> results;

    @Column(name = "dimension_reqs")
    private String dimensionRequirements;

    @Embedded
    private Timestamps timestamps;
}
