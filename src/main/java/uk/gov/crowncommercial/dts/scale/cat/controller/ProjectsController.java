package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Collection;
import javax.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.StringValueResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;

/**
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class ProjectsController extends AbstractRestController {

  private final ProcurementProjectService procurementProjectService;

  @GetMapping
  @TrackExecutionTime
  public Collection<ProjectPackageSummary> getProjects(
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    log.info("getProjects invoked on behalf of principal: {}", principal);
    return procurementProjectService.getProjects(principal);
  }

  @PostMapping("/agreements")
  @TrackExecutionTime
  public DraftProcurementProject createProcurementProject(
      @Valid @RequestBody final AgreementDetails agreementDetails,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    var conclaveOrgId = getCiiOrgIdFromJwt(authentication);
    log.info("createProcurementProject invoked on behalf of principal: {}", principal,
        conclaveOrgId);

    return procurementProjectService.createFromAgreementDetails(agreementDetails, principal,
        conclaveOrgId);
  }

  @PutMapping("/{procID}/name")
  @TrackExecutionTime
  public String updateProcurementProjectName(@PathVariable("procID") final Integer procId,
      @RequestBody final ProcurementProjectName projectName,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("updateProcurementEventName invoked on behalf of principal: {}", principal);

    procurementProjectService.updateProcurementProjectName(procId, projectName.getName(),
        principal);

    return Constants.OK_MSG;
  }

  @PutMapping("/{procID}/close")
  @TrackExecutionTime
  public StringValueResponse closeProcurementProject(@PathVariable("procID") final Integer procId,
      @RequestBody final TerminationType terminationType,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("closeProcurementProject invoked on behalf of principal: {}", principal);

    procurementProjectService.closeProcurementProject(procId, terminationType, principal);

    return new StringValueResponse("OK");
  }

  @GetMapping("/{proc-id}/event-types")
  @TrackExecutionTime
  public Collection<ProjectEventType> listProcurementEventTypes(
      @PathVariable("proc-id") final Integer procId, final JwtAuthenticationToken authentication) {

    log.info("listProcurementEventTypes by project invoked on behalf of principal: {}",
        getPrincipalFromJwt(authentication));

    return procurementProjectService.getProjectEventTypes(procId);
  }

  @GetMapping("/{proc-id}/users")
  @TrackExecutionTime
  public Collection<TeamMember> getProjectUsers(@PathVariable("proc-id") final Integer procId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getProjectUsers invoked on behalf of principal: {}", principal);

    return procurementProjectService.getProjectTeamMembers(procId, principal);
  }

  @PutMapping("/{proc-id}/users/{user-id}")
  @TrackExecutionTime
  public TeamMember addProjectUser(@PathVariable("proc-id") final Integer procId,
      @PathVariable("user-id") final String userId,
      @RequestBody final UpdateTeamMember updateTeamMember,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("addProjectUser invoked on behalf of principal: {}", principal);

    return procurementProjectService.addProjectTeamMember(procId, userId, updateTeamMember,
        principal);
  }

  @DeleteMapping("/{proc-id}/users/{user-id}")
  @TrackExecutionTime
  public String deleteTeamMember(@PathVariable("proc-id") final Integer procId,
      @PathVariable("user-id") final String userId, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("deleteTeamMember invoked on behalf of principal: {}", principal);
    procurementProjectService.deleteTeamMember(procId, userId, principal);
    return Constants.OK_MSG;
  }

}
