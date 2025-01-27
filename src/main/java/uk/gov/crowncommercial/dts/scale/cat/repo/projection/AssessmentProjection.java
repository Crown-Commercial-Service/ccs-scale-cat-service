package uk.gov.crowncommercial.dts.scale.cat.repo.projection;

import org.springframework.beans.factory.annotation.Value;

public interface AssessmentProjection {

    @Value("#{target.assessmentId}")
    Integer getAssessmentId();


    @Value("#{target.assessmentName}")
    String getAssessmentName();

    @Value("#{target.externalToolId}")
    String getExternalToolId();

    @Value("#{target.status}")
    String getStatus();


}
