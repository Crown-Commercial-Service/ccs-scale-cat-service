package uk.gov.crowncommercial.dts.scale.cat.controller;

import java.util.Map;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DraftProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;

/**
 *
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProjectsController {

  private final ProcurementProjectService procurementProjectService;

  // @PreAuthorize("isAuthenticated()")
  @PostMapping("/tenders/projects/agreements")
  public DraftProcurementProject createProcurementProject(
      @RequestBody AgreementDetails agreementDetails, JwtAuthenticationToken authentication) {

    var principal = authentication.getTokenAttributes().get("sub").toString();
    log.info("createProcuremenProject invoked on bahelf of principal: {}", principal);

    return procurementProjectService.createFromAgreementDetails(agreementDetails, principal);
  }

  @PostMapping("/tenders/projects/{procID}/name")
  public String updateProcurementProjectName(@PathVariable("procID") Integer procId,
      @RequestBody Map<String, String> request, JwtAuthenticationToken authentication) {

    var principal = authentication.getTokenAttributes().get("sub").toString();
    log.info("updateProcurementEventName invoked on behalf of principal: {}", principal);

    // TODO - defining RequestBody as Map - to revisit
    procurementProjectService.updateProcurementProjectName(procId,
        request.get(Constants.ATTRIBUTE_NAME));

    return Constants.OK;
  }
}
