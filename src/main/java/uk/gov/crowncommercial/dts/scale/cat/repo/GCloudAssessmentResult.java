package uk.gov.crowncommercial.dts.scale.cat.repo;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudAssessmentEntity;

import javax.persistence.*;

@Entity
@Table(name = "gcloud_assessment_results")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GCloudAssessmentResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gcloud_result_id")
    Integer id;

    @Column(name = "assessment_id")
    private Integer assessmentId;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "service_desc")
    private String serviceDescription;

    @Column(name = "service_link")
    private String serviceLink;

    @Embedded
    private Timestamps timestamps;
}
