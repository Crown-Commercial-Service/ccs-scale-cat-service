package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.projection.AssessmentProjection;

public interface AssessmentRepo extends JpaRepository<AssessmentEntity, Integer> {

  Set<AssessmentEntity> findByTimestampsCreatedBy(final String userId);

  @Query("select ae from AssessmentEntity ae" +
          " LEFT OUTER JOIN ae.tool tl " +
          " WHERE ae.timestamps.createdBy = :userId AND tl.externalToolId = :externalToolId ")
  Set<AssessmentEntity> findByTimestampsCreatedBy(final String userId,final String externalToolId);



  @Query(value = "SELECT * FROM ( (SELECT ass.assessment_id AS assessmentId , " +
          "                          ass.assessment_name AS assessmentName ,  " +
          "                          aTools.assessment_tool_id AS externalToolId ," +
          "                          ass.status AS status  " +
          "                      FROM ASSESSMENTS ass " +
          "                         LEFT OUTER JOIN  ASSESSMENT_TOOLS aTools" +
          "                             ON aTools.assessment_tool_id = ass.assessment_tool_id " +
          "                       WHERE ass.created_by = :userId " +
          "                           AND CAST (aTools.external_assessment_tool_id AS INTEGER)  = :externalToolId )" +
          "              UNION                              " +
          "                     ( SELECT gass.assessment_id AS assessmentId, " +
          "                          gass.assessment_name AS assessmentName ,  " +
          "                          gass.external_tool_id AS externalToolId ," +
          "                          gass.status AS status  " +
          "                     FROM gcloud_assessments gass "  +
          "                      WHERE gass.created_by = :userId AND gass.external_tool_id =:externalToolId  )" +
          " ) AS ASS_QUERY " +
          "", nativeQuery = true)
  Set<AssessmentProjection> findAssessmentsByCreatedByAndExternalToolId(final String userId, final Integer externalToolId);


  @Query(value = "SELECT * FROM ( (SELECT ass.assessment_id AS assessmentId , " +
          "                          ass.assessment_name AS assessmentName ,  " +
          "                          aTools.assessment_tool_id AS externalToolId ," +
          "                          ass.status AS status  " +
          "                      FROM ASSESSMENTS ass " +
          "                         LEFT OUTER JOIN  ASSESSMENT_TOOLS aTools" +
          "                             ON aTools.assessment_tool_id = ass.assessment_tool_id " +
          "                       WHERE ass.created_by = :userId " +
          "                           )" +
          "              UNION                              " +
          "                     ( SELECT gass.assessment_id AS assessmentId, " +
          "                          gass.assessment_name AS assessmentName ,  " +
          "                          gass.external_tool_id AS externalToolId ," +
          "                          gass.status AS status  " +
          "                     FROM gcloud_assessments gass "  +
          "                      WHERE gass.created_by = :userId )" +
          " ) AS ASS_QUERY " +
          "", nativeQuery = true)
  Set<AssessmentProjection> findAssessmentsByCreatedBy(final String userId);

}
