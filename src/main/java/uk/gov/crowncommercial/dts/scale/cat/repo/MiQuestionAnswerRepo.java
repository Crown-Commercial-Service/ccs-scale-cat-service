package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.MiQuestionAnswerEntity;

import java.util.List;

/** E-procurement Question and Answer repo to perform CRUD operations. */
public interface MiQuestionAnswerRepo extends JpaRepository<MiQuestionAnswerEntity, Integer> {

  List<MiQuestionAnswerEntity> findByCreatedBy(String createdBy);

  List<MiQuestionAnswerEntity> findAllByProjectIdIgnoreCase(String projectId);
}
