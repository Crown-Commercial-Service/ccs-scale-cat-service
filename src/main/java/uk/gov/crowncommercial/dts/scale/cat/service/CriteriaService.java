package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Set;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EvalCriteria;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class CriteriaService {

  private final AgreementsService agreementsService;

  public Set<EvalCriteria> getEvalCriteria() {
    throw new NotImplementedException();
  }

}
