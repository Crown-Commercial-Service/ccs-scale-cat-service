package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerUserExistException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse.UserActionEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.service.ProfileManagementService;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

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
  public Collection<ViewEventType> listProcurementEventTypes(
      final JwtAuthenticationToken authentication) {

    log.info("listProcurementEventTypes invoked on behalf of principal: {}",
        getPrincipalFromJwt(authentication));

    return Arrays.asList(ViewEventType.values());
  }

  @GetMapping("/users/{user-id}")
  public GetUserResponse getUser(@PathVariable("user-id") final String userId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);

    log.info("getUserRoles invoked on behalf of principal: {} for user-id: {}", principal, userId);

    if (!Objects.equals(principal, userId)) {
      // CON-1680-AC3
      throw new AuthorisationFailureException(
          "Authenticated user does not match requested user-id");
    }

    return new GetUserResponse().roles(profileManagementService.getUserRoles(userId));
  }

  @PutMapping("/users/{user-id}")
  public ResponseEntity<RegisterUserResponse> registerUser(
      @PathVariable("user-id") final String userId, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);

    log.info("registerUser invoked on behalf of principal: {} for user-id: {}", principal, userId);

    if (!Objects.equals(principal, userId)) {
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
}
