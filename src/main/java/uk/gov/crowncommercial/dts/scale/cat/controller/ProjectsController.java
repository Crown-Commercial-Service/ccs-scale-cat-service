package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Collection;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;

/**
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class ProjectsController extends AbstractRestController {

  private final ProcurementProjectService procurementProjectService;

  @PostMapping("/agreements")
  public DraftProcurementProject createProcurementProject(
      @RequestBody final AgreementDetails agreementDetails,
      final JwtAuthenticationToken authentication) {

    final var principal = getPrincipalFromJwt(authentication);
    log.info("createProcuremenProject invoked on bahelf of principal: {}", principal);

    return procurementProjectService.createFromAgreementDetails(agreementDetails, principal);
  }

  @PutMapping("/{procID}/name")
  public String updateProcurementProjectName(@PathVariable("procID") final Integer procId,
      @RequestBody final ProcurementProjectName projectName,
      final JwtAuthenticationToken authentication) {

    final var principal = getPrincipalFromJwt(authentication);
    log.info("updateProcurementEventName invoked on behalf of principal: {}", principal);

    procurementProjectService.updateProcurementProjectName(procId, projectName.getName(),
        principal);

    return Constants.OK;
  }

  @GetMapping("/{proc-id}/event-types")
  public Collection<EventType> listProcurementEventTypes(
      @PathVariable("proc-id") final Integer procId, final JwtAuthenticationToken authentication) {

    log.info("listProcurementEventTypes by project invoked on behalf of principal: {}",
        getPrincipalFromJwt(authentication));

    return procurementProjectService.getProjectEventTypes(procId);
  }

  @GetMapping("/{proc-id}/users")
  public Collection<TeamMember> getProjectUsers(@PathVariable("proc-id") final Integer procId,
      final JwtAuthenticationToken authentication) {

    log.info("getProjectUsers invoked on behalf of principal: {}",
        getPrincipalFromJwt(authentication));

    return procurementProjectService.getProjectTeamMembers(procId);
  }
}
