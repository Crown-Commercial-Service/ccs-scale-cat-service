package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EvalCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventType;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class CriteriaService {

  private final AgreementsService agreementsService;
  private final ValidationService validationService;

  public Set<EvalCriteria> getEvalCriteria(final Integer projectId, final String eventId) {

    // Get project from tenders DB to obtain Jaggaer project id
    final var event = validationService.validateProjectAndEventIds(projectId, eventId);

    // Call the AS to get the template criteria
    final var lotEventTypeDataTemplates =
        agreementsService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), EventType.fromValue(event.getEventType()));

    // TODO: Decide how to handle multiple data templates being returned by AS
    final var dataTemplate = lotEventTypeDataTemplates.stream().findFirst()
        .orElseThrow(() -> new AgreementsServiceApplicationException("Data template not found"));

    // Convert to EvalCriteria and return
    return dataTemplate.getCriteria().stream().map(tc -> {
      final var evalCriteria = new EvalCriteria();
      evalCriteria.setId(tc.getId());
      evalCriteria.setDescription(tc.getDescription());
      evalCriteria.setTitle(tc.getTitle());
      return evalCriteria;
    }).collect(Collectors.toSet());
  }

}
