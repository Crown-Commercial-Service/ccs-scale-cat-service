package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EvalCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventType;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class CriteriaService {

  private final AgreementsService agreementsService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final ValidationService validationService;

  public Set<EvalCriteria> getEvalCriteria(final Integer projectId, final String eventId) {

    // Get project from tenders DB to obtain Jaggaer project id
    final var event = validationService.validateProjectAndEventIds(projectId, eventId);

    // Call the AS to get the template criteria
    final var templateCriteria =
        agreementsService.getLotEventTemplateCriteria(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), EventType.fromValue(event.getEventType()));

    // Convert to EvalCriteria and return
    return templateCriteria.stream().map(tc -> {
      final var evalCriteria = new EvalCriteria();
      evalCriteria.setId(tc.getId());
      evalCriteria.setDescription(tc.getDescription());
      evalCriteria.setTitle(tc.getTitle());
      return evalCriteria;
    }).collect(Collectors.toSet());
  }

}
