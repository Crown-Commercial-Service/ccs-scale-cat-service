package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static uk.gov.crowncommercial.dts.scale.cat.service.scheduler.ProjectsCSVGenerationScheduledTask.CSV_FILE_NAME;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.StringValueResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DraftProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProcurementProjectName;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectFilters;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPackage;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPackageSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPublicSearchResult;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TeamMember;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TerminationEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateTeamMember;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.OcdsSections;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.ProjectPackageService;
import uk.gov.crowncommercial.dts.scale.cat.utils.SanitisationUtils;

/**
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class ProjectsController extends AbstractRestController {

  private final ProjectPackageService projectPackageService;
  private final ProcurementProjectService procurementProjectService;
  private final SanitisationUtils sanitisationUtils;

  private final ObjectMapper mapper;

  //search-type=projectName&search-term=My%20search%20term&page=1&page-size=20'
  @GetMapping(value={"", "/"})
  @TrackExecutionTime
  public Collection<ProjectPackageSummary> getProjects(@RequestParam(name = "search-type", required = false) final String searchType,
                                                       @RequestParam(name = "search-term", required = false) final String searchTerm,
                                                       @RequestParam(name = "page", defaultValue ="0", required = false) final String page,
                                                       @RequestParam(name = "page-size",  defaultValue = "20",required = false) final String pageSize,
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    log.info("getProjects invoked on behalf of principal: {}", principal);
    return procurementProjectService.getProjects(principal, searchType, searchTerm, page, pageSize);
  }

  /**
   * Returns a single ProjectPackageSummary model for a given project ID
   */
  @GetMapping("/{projectId}/summary")
  @TrackExecutionTime
  public ProjectPackageSummary getProjectSummary(@PathVariable("projectId") final Integer projectId, final JwtAuthenticationToken authentication) {
    // Grab the principal from the JWT passed to us, then use it and the requested Project ID to fetch the relevant project summary
    String principal = getPrincipalFromJwt(authentication);
    return procurementProjectService.getProjectSummary(principal, projectId);
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

  @GetMapping("/{procID}")
  @TrackExecutionTime
  public ProjectPackage getProject(@PathVariable("procID") final Integer procId,
                                   @RequestParam(name = "group", required = false, defaultValue = "summary") final String group,
                                   final JwtAuthenticationToken authentication) {
    var principal = null != authentication ? getPrincipalFromJwt(authentication) : "";
    return projectPackageService.getProjectPackage(procId, principal, OcdsSections.getSection(group));
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
      @RequestBody @Valid final TerminationEvent request,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("closeProcurementProject invoked on behalf of principal: {}", principal);

    procurementProjectService.closeProcurementProject(procId, request.getTerminationType(), principal);

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
    String sanitisedUserId = sanitisationUtils.sanitiseString(userId, false);

    return procurementProjectService.addProjectTeamMember(procId, sanitisedUserId, updateTeamMember, principal);
  }

  @DeleteMapping("/{proc-id}/users/{user-id}")
  @TrackExecutionTime
  public String deleteTeamMember(@PathVariable("proc-id") final Integer procId,
      @PathVariable("user-id") final String userId, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("deleteTeamMember invoked on behalf of principal: {}", principal);
    String sanitisedUserId = sanitisationUtils.sanitiseString(userId, false);

    procurementProjectService.deleteTeamMember(procId, sanitisedUserId, principal);
    return Constants.OK_MSG;
  }
  
  @GetMapping(value = "/download")
  public void downloadFile(HttpServletResponse response) throws IOException {
    var downloadProjectsData = procurementProjectService.downloadProjectsData();
    response.setContentType(MediaType.TEXT_PLAIN.toString());
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + CSV_FILE_NAME + "\"");
    IOUtils.copy(downloadProjectsData, response.getOutputStream());
    response.flushBuffer();
  }

  @SneakyThrows
  @GetMapping("/search")
  @TrackExecutionTime
  public ProjectPublicSearchResult getProjectsSummary(@RequestParam(name = "agreement-id", required = true) final String agreementId,
                                                   @RequestParam(name = "keyword", required = false) final String keyword,
                                                   @RequestParam(name= "lot-id", required = false) final String lotId,
                                                   @RequestParam(name = "page", defaultValue ="1", required = false) final String page,
                                                   @RequestParam(name = "page-size",  defaultValue = "20",required = false) final String pageSize,
                                                   @RequestParam (name = "filters", required = false) final String filters) {
    ProjectFilters projectFilters=null;
    int pageNo = Integer.parseInt(page);
    int size = Integer.parseInt(pageSize);
    if(filters != null) {
      String decodedString = new String(Base64.getDecoder().decode(filters));
       projectFilters = mapper.readValue(decodedString, ProjectFilters.class);
    }
    return procurementProjectService.getProjectSummery(keyword,lotId,
     pageNo, size, projectFilters);
  }
}