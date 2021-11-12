package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.lang.String.format;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UserRolesConflictException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.RolePermissionInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum;

/**
 * Coordinating service for Conclave / Jaggaer Org and User profile operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileManagementService {

  static final String SYSID_CONCLAVE = "Conclave";
  static final String SYSID_JAGGAER = "Jaggaer";
  static final String ERR_MSG_FMT_ROLES_CONFLICT =
      "User [%s] has conflicting Conclave/Jaggaer roles (Conclave: %s, Jaggaer: %s)";
  static final String ERR_MSG_FMT_CONCLAVE_USER_MISSING = "User [%s] not found in Conclave";
  static final String ERR_MSG_FMT_JAGGAER_USER_MISSING = "User [%s] not found in Jaggaer";
  static final String ERR_MSG_FMT_NO_ROLES = "User [%s] is neither buyer NOR supplier in Conclave";
  static final String MSG_FMT_SYS_ROLES = "%s user [%s] has roles %s";
  static final String MSG_FMT_BOTH_ROLES = "User [%s] is both buyer AND supplier in Conclave";

  private final ConclaveWrapperApiService conclaveService;
  private final ConclaveAPIConfig conclaveAPIConfig;
  private final UserProfileService userProfileService;

  public List<RolesEnum> getUserRoles(final String userId) {
    Assert.hasLength(userId, "userId must not be empty");
    var conclaveUser = conclaveService.getUserDetails(userId)
        // CON-1680-AC2(a)
        .orElseThrow(
            () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, userId)));

    var conclaveBuyerSupplierRoleKeys = Map.of(conclaveAPIConfig.getBuyerRoleKey(), RolesEnum.BUYER,
        conclaveAPIConfig.getSupplierRoleKey(), RolesEnum.SUPPLIER);

    var sysRoleMappings =
        Map.of(SYSID_CONCLAVE, new HashSet<RolesEnum>(), SYSID_JAGGAER, new HashSet<RolesEnum>());

    conclaveUser.getDetail().getRolePermissionInfo().stream().map(RolePermissionInfo::getRoleKey)
        .filter(conclaveBuyerSupplierRoleKeys::containsKey).forEach(
            rk -> sysRoleMappings.get(SYSID_CONCLAVE).add(conclaveBuyerSupplierRoleKeys.get(rk)));
    log.debug(
        format(MSG_FMT_SYS_ROLES, SYSID_CONCLAVE, userId, sysRoleMappings.get(SYSID_CONCLAVE)));
    validateBuyerSupplierConclaveRoles(sysRoleMappings.get(SYSID_CONCLAVE), userId);

    userProfileService.resolveBuyerUserByEmail(userId)
        .ifPresent(su -> sysRoleMappings.get(SYSID_JAGGAER).add(RolesEnum.BUYER));
    userProfileService.resolveSupplierData(userId)
        .ifPresent(scd -> sysRoleMappings.get(SYSID_JAGGAER).add(RolesEnum.SUPPLIER));
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_JAGGAER, userId, sysRoleMappings.get(SYSID_JAGGAER)));

    if (sysRoleMappings.get(SYSID_JAGGAER).isEmpty()) {
      // CON-1680-AC2(b)
      throw new ResourceNotFoundException(format(ERR_MSG_FMT_JAGGAER_USER_MISSING, userId));
    }

    if (!Objects.equals(sysRoleMappings.get(SYSID_CONCLAVE), sysRoleMappings.get(SYSID_JAGGAER))) {
      // CON-1680-AC5&6
      throw new UserRolesConflictException(format(ERR_MSG_FMT_ROLES_CONFLICT, userId,
          sysRoleMappings.get(SYSID_CONCLAVE), sysRoleMappings.get(SYSID_JAGGAER)));
    }
    return List.copyOf(sysRoleMappings.get(SYSID_CONCLAVE));
  }

  private void validateBuyerSupplierConclaveRoles(final Collection<RolesEnum> roleKeys,
      final String userId) {
    if (roleKeys == null || roleKeys.isEmpty()) {
      // CON-1680-AC4
      throw new UserRolesConflictException(format(ERR_MSG_FMT_NO_ROLES, userId));
    }

    if (roleKeys.size() >= 2) {
      // CON-1680-AC7
      log.warn(format(MSG_FMT_BOTH_ROLES, userId));
    }
  }

}
