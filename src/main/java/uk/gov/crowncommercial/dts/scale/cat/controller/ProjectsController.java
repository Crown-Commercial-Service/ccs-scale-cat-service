package uk.gov.crowncommercial.dts.scale.cat.controller;

import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
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
public class ProjectsController {

  private final Rollbar rollbar;
  private final ProjectService projectService;

  @PostMapping("/tenders/ProcurementProject/agreements")
  public Set<AgreementSummary> createProcurementProject() {
    rollbar.debug("GET agreement summaries invoked");
    return Set.of(projectService.findAll());
  }

}
