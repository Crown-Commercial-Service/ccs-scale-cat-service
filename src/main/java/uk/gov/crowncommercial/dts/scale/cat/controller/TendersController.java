package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse.UserActionEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.User;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.service.ProfileManagementService;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

  @GetMapping("/event-types")
  @TrackExecutionTime
  public Collection<ViewEventType> listProcurementEventTypes(
      final JwtAuthenticationToken authentication) {

    log.info("listProcurementEventTypes invoked on behalf of principal: {}",
        getPrincipalFromJwt(authentication));

    return Arrays.asList(ViewEventType.values());
  }

  /**
   * Gets the status of a requested user, generated from both their state in PPG and Jaegger
   */
  @GetMapping("/users/{user-id}")
  @TrackExecutionTime
  public GetUserResponse getUser(@PathVariable("user-id") final String userId, final JwtAuthenticationToken authentication) {
    String principal = getPrincipalFromJwt(authentication);

    log.info("getUserRoles invoked on behalf of principal: {} for user-id: {}", principal, userId);

    // Make sure the user requested is the same as the principal in session, or we can't service the request
    if (!StringUtils.equalsIgnoreCase(trim(principal), trim(userId))) {
      throw new AuthorisationFailureException("Authenticated user does not match requested user-id");
    }

    // Requested user and principal match, so we can proceed
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
      return ResponseEntity.status(HttpStatus.OK).body(registerUserResponse);
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
}
