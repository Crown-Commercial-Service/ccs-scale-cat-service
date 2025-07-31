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
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse.UserActionEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.User;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateCompanyResponse;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProfileManagementService;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

  private final ConclaveService conclaveService;

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

  /**
   * Search for any user accounts matching the given email-based search term
   */
  @GetMapping("/users/search/{search-term}")
  public UserProfileResponseInfo GetUserSearch(@PathVariable("search-term") final String searchTerm, final JwtAuthenticationToken auth) {
    getPrincipalFromJwt(auth);

    // Technically this "search" is just a direct lookup for now, as we can't do partial matches because of how the data is stored
    Optional<UserProfileResponseInfo> matchingUser = conclaveService.getUserProfile(searchTerm);

      return matchingUser.orElse(null);
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

  /**
   * Link or unlink the SSO config for any existing buyer account, matching the given email.
   */
  @PutMapping("/users/{user-id}/sso")
  @TrackExecutionTime
  public ResponseEntity<CreateUpdateCompanyResponse> updateBuyerSso(
    @PathVariable("user-id") final String userId,
    @PathVariable("request-type") final String requestType,
    final JwtAuthenticationToken authentication
  ) {

    var principal = getPrincipalFromJwt(authentication);

    log.info("updateUserSso invoked on behalf of principal: {} for user-id: {}", principal, userId);

    if (principal == null || principal.isEmpty()) {
      throw new AuthorisationFailureException(
          "Not Authenticated.");
    }

    CreateUpdateCompanyResponse response = profileManagementService.updateBuyerSso(userId, requestType);

    if (response.getReturnCode() == "409") {
      return ResponseEntity.status(HttpStatus.OK).body(response);
    } else {
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
