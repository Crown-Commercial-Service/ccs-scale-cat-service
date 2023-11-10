package uk.gov.crowncommercial.dts.scale.cat.model.assessment;

import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentStatus;

import java.time.OffsetDateTime;

/**
 * Model containing summary information about a specific GCloud assessment
 */
public class GCloudAssessmentSummary {
    public Integer id;

    public String name;

    public String criteria;

    public String description;

    public OffsetDateTime lastUpdate;

    public AssessmentStatus status;
}
