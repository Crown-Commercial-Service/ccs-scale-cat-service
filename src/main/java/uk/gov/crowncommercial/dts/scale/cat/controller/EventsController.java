package uk.gov.crowncommercial.dts.scale.cat.controller;

import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.rollbar.notifier.Rollbar;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.AgreementSummary;
import uk.gov.crowncommercial.dts.scale.cat.service.ProjectService;

/**
 *
 */
@RestController
@RequiredArgsConstructor
public class EventsController {

  private final ProjectService projectService;
  private final Rollbar rollbar;

  @GetMapping("/agreement-summaries")
  public Set<AgreementSummary> getAgreementSummaries() {
    rollbar.debug("GET agreement summaries invoked");
    return Set.of(projectService.findAll());
  }

}
