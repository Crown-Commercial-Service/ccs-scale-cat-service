package uk.gov.crowncommercial.dts.scale.cat.model.assessment;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentStatus;

import java.time.OffsetDateTime;

/**
 * Model containing summary information about a specific GCloud assessment
 */
public class GCloudAssessmentSummary {
    @JsonProperty("assessment-id")
    public Integer id;

    public String assessmentName;

    public String dimensionRequirements;

    public String resultsSummary;

    public OffsetDateTime lastUpdate;

    public AssessmentStatus status;
}
