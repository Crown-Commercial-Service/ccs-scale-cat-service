package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Arrays;
import java.util.Collection;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.google.common.base.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.service.ProfileManagementService;

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

    if (!Objects.equal(principal, userId)) {
      // CON-1680-AC3
      throw new AuthorisationFailureException(
          "Authenticated user does not match requested user-id");
    }

    return new GetUserResponse().roles(profileManagementService.getUserRoles(userId));
  }

}
