package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

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

    @Column(name = "external_tool_id")
    private Integer externalToolId;

    @ToString.Exclude
    @OneToMany(mappedBy="assessmentId", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    Set<GCloudAssessmentResult> results;

    @Column(name = "dimension_reqs")
    private String dimensionRequirements;

    @Column(name = "results_summary")
    private String resultsSummary;

    @Embedded
    private Timestamps timestamps;
}
