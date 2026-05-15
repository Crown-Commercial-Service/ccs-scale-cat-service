package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.MiQuestionAnswerEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.MiQuestionsEntity;

/**
 * E-procurement Question repo to perform CRUD operations.
 */
public interface MiQuestionRepo extends JpaRepository<MiQuestionsEntity, Integer> {
}