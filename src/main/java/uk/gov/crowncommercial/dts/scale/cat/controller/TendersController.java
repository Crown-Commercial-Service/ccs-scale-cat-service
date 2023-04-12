package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyAuthToken;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerUserExistException;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse.UserActionEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Release;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.SalesforceProjectTender;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.SalesforceProjectTender200Response;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.User;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.service.ProfileManagementService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.validation.Valid;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.apache.commons.lang3.StringUtils.trim;

/**
 * Tenders ('Base Data' tag in YAML)
 */
@RestController
@RequestMapping(path = "/tenders", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class TendersController extends AbstractRestController {

  private final ProfileManagementService profileManagementService;
  private final ProcurementProjectService procurementProjectService;
  private final ProcurementEventService procurementEventService;
  private final JaggaerAPIConfig jaggaerAPIConfig;

  @GetMapping("/event-types")
  @TrackExecutionTime
  public Collection<ViewEventType> listProcurementEventTypes(
      final JwtAuthenticationToken authentication) {

    log.info("listProcurementEventTypes invoked on behalf of principal: {}",
        getPrincipalFromJwt(authentication));

    return Arrays.asList(ViewEventType.values());
  }

  @GetMapping("/users/{user-id}")
  @TrackExecutionTime
  public GetUserResponse getUser(@PathVariable("user-id") final String userId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);

    log.info("getUserRoles invoked on behalf of principal: {} for user-id: {}", principal, userId);

    if (!StringUtils.equalsIgnoreCase(trim(principal), trim(userId))) {
      // CON-1680-AC3
      throw new AuthorisationFailureException(
          "Authenticated user does not match requested user-id");
    }

    return new GetUserResponse().roles(profileManagementService.getUserRoles(userId));
  }

  @PutMapping("/users/{user-id}")
  @TrackExecutionTime
  public ResponseEntity<RegisterUserResponse> registerUser(
      @PathVariable("user-id") final String userId, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);

    log.info("registerUser invoked on behalf of principal: {} for user-id: {}", principal, userId);

    if (!StringUtils.equalsIgnoreCase(trim(principal), trim(userId))) {
      // CON-1682-AC11
      throw new AuthorisationFailureException(
          "Authenticated user does not match requested user-id");
    }
    var registerUserResponse = profileManagementService.registerUser(userId);
    if (registerUserResponse.getUserAction() == UserActionEnum.EXISTED) {
      throw new JaggaerUserExistException("Jaggaer sub or super user already exists");
    } else {
      return ResponseEntity.status(HttpStatus.CREATED).body(registerUserResponse);
    }
  }


  @GetMapping("/users")
  @TrackExecutionTime
  public List<User> getOrgUsers(final JwtAuthenticationToken authentication,@RequestParam(name="org-id",required = false) final String organisationId) {

   var principal = getPrincipalFromJwt(authentication);

   log.info("getAllUsers for on behalf of principal: {} ", principal);
   // We are not retrieving org users based on organisationId
   return profileManagementService.getOrgUsers(principal);
  }
  
  @PostMapping("/projects/salesforce")
  @TrackExecutionTime

  public SalesforceProjectTender200Response createProcurementCase(@Valid @RequestBody SalesforceProjectTender projectTender,
		  final ApiKeyAuthToken authentication) {
	  
	  String principal = (String) authentication.getPrincipal();
	  
	  log.debug("createProcurementCase() with principal -> {}", principal);

	 // 
	 return procurementProjectService.createFromSalesforceDetails(projectTender,principal);
	    
  }

  @GetMapping("/projects/deltas")
  @TrackExecutionTime
  public List<Release> getProjectsDeltas(
          @RequestParam(name = "lastSuccessRun", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Date lastSuccessRun,
          final ApiKeyAuthToken authentication) {
      
      return procurementEventService.getProjectUpdatesByLastUpdateDate(lastSuccessRun, jaggaerAPIConfig.getAssistedProcurementId() );
  }
}
