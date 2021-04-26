package uk.gov.crowncommercial.dts.scale.cat.controller;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.rollbar.notifier.Rollbar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DraftProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerUserProfileService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProjectService;

/**
 *
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProjectsController {

  private final Rollbar rollbar;
  private final JaggaerUserProfileService jaggaerUserProfileService;
  private final ProjectService projectService;

  // @PreAuthorize("hasAuthority('ORG_ADMINISTRATOR')")
  @PostMapping("/tenders/projects/agreements")
  public DraftProcurementProject createProcurementProject(
      @RequestBody AgreementDetails agreementDetails, JwtAuthenticationToken authentication) {
    rollbar.debug("POST createProcurementProject invoked");

    String principal = authentication.getTokenAttributes().get("sub").toString();
    String jaggaerUserId = jaggaerUserProfileService.resolveJaggaerUserId(principal);
    log.info("createProcuremenProject invoked on bahelf of principal: {}, jaggaerUserId: {}",
        principal, jaggaerUserId);

    return projectService.createProjectFromAgreement(agreementDetails, jaggaerUserId);
  }

}
