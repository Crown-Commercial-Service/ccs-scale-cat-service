package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.QuestionAndAnswer;

/**
 *
 */
@Repository
public interface QuestionAndAnswerRepo extends JpaRepository<QuestionAndAnswer, Integer> {

  Set<QuestionAndAnswer> findByEventId(Integer eventId);

  Optional<QuestionAndAnswer> findByIdAndEventId(Integer questionId, Integer eventId);
  
  long countByEventId(Integer eventId);

}
