package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UserRolesConflictException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.RolePermissionInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserResponseDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnSubUser.SubUser;

/**
 *
 */
@SpringBootTest(classes = {ProfileManagementService.class, ConclaveAPIConfig.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(ConclaveAPIConfig.class)
class ProfileManagementServiceTest {

  private static final String USERID = "john.smith@example.com";
  private static final String ROLEKEY_BUYER = "JAEGGER_BUYER";
  private static final String ROLEKEY_SUPPLIER = "JAEGGER_SUPPLIER";

  @MockBean
  private ConclaveWrapperApiService conclaveService;

  @MockBean
  private UserProfileService userProfileService;

  @Autowired
  private ProfileManagementService profileManagementService;

  /*
   * CON-1680-AC1(a)
   */
  @Test
  void testGetUserRolesConclaveBuyerJaggaerBuyer() {

    var rolePermissionInfo = new RolePermissionInfo().roleKey(ROLEKEY_BUYER);
    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(List.of(rolePermissionInfo)));

    when(conclaveService.getUserDetails(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserByEmail(USERID))
        .thenReturn(Optional.of(SubUser.builder().build()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(1, userRoles.size());
    assertEquals(RolesEnum.BUYER, userRoles.get(0));
  }

  /*
   * CON-1680-AC1(b)
   */
  @Test
  void testGetUserRolesConclaveSupplierJaggaerSupplier() {

    var rolePermissionInfo = new RolePermissionInfo().roleKey(ROLEKEY_SUPPLIER);
    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(List.of(rolePermissionInfo)));

    when(conclaveService.getUserDetails(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveSupplierData(USERID))
        .thenReturn(Optional.of(new ReturnCompanyData()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(1, userRoles.size());
    assertEquals(RolesEnum.SUPPLIER, userRoles.get(0));
  }

  /*
   * CON-1680-AC2(a)
   */
  @Test
  void testGetUserRolesNoConclaveUserThrowsResourceNotFound() {

    when(conclaveService.getUserDetails(USERID)).thenReturn(Optional.empty());

    var ex = assertThrows(ResourceNotFoundException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals("User [" + USERID + "] not found in Conclave", ex.getMessage());
  }

  /*
   * CON-1680-AC2(b)
   */
  @Test
  void testGetUserRolesNoJaggaerUserThrowsResourceNotFound() {

    var rolePermissionInfo = new RolePermissionInfo().roleKey(ROLEKEY_BUYER);
    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(List.of(rolePermissionInfo)));

    when(conclaveService.getUserDetails(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserByEmail(USERID)).thenReturn(Optional.empty());
    when(userProfileService.resolveSupplierData(USERID)).thenReturn(Optional.empty());

    var ex = assertThrows(ResourceNotFoundException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals("User [" + USERID + "] not found in Jaggaer", ex.getMessage());
  }

  /*
   * CON-1680-AC4
   */
  @Test
  void testGetUserRolesNoBuyerSupplierRoleThrowsUserRolesConflictException() {

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(new ArrayList<>()));

    when(conclaveService.getUserDetails(USERID)).thenReturn(Optional.of(userProfileResponseInfo));

    var ex = assertThrows(UserRolesConflictException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals("User [" + USERID + "] is neither buyer NOR supplier in Conclave",
        ex.getMessage());
  }

  /*
   * CON-1680-AC5
   */
  @Test
  void testGetUserRolesConclaveSupplierJaggaerBuyer() {

    var rolePermissionInfoSupplier = new RolePermissionInfo().roleKey(ROLEKEY_SUPPLIER);
    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(List.of(rolePermissionInfoSupplier)));

    when(conclaveService.getUserDetails(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserByEmail(USERID))
        .thenReturn(Optional.of(SubUser.builder().build()));
    when(userProfileService.resolveSupplierData(USERID)).thenReturn(Optional.empty());

    var ex = assertThrows(UserRolesConflictException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals(
        "User [" + USERID
            + "] has conflicting Conclave/Jaggaer roles (Conclave: [supplier], Jaggaer: [buyer])",
        ex.getMessage());
  }

  /*
   * CON-1680-AC6
   */
  @Test
  void testGetUserRolesConclaveBuyerJaggaerSupplier() {

    var rolePermissionInfoSupplier = new RolePermissionInfo().roleKey(ROLEKEY_BUYER);
    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(List.of(rolePermissionInfoSupplier)));

    when(conclaveService.getUserDetails(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserByEmail(USERID)).thenReturn(Optional.empty());
    when(userProfileService.resolveSupplierData(USERID))
        .thenReturn(Optional.of(new ReturnCompanyData()));

    var ex = assertThrows(UserRolesConflictException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals(
        "User [" + USERID
            + "] has conflicting Conclave/Jaggaer roles (Conclave: [buyer], Jaggaer: [supplier])",
        ex.getMessage());
  }

  /*
   * CON-1680-AC17
   */
  @Test
  void testGetUserRolesConclaveBothJaggaerBoth() {

    var rolePermissionInfoBuyer = new RolePermissionInfo().roleKey(ROLEKEY_BUYER);
    var rolePermissionInfoSupplier = new RolePermissionInfo().roleKey(ROLEKEY_SUPPLIER);
    var userProfileResponseInfo =
        new UserProfileResponseInfo().userName(USERID).detail(new UserResponseDetail()
            .rolePermissionInfo(List.of(rolePermissionInfoBuyer, rolePermissionInfoSupplier)));

    when(conclaveService.getUserDetails(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserByEmail(USERID))
        .thenReturn(Optional.of(SubUser.builder().build()));
    when(userProfileService.resolveSupplierData(USERID))
        .thenReturn(Optional.of(new ReturnCompanyData()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(2, userRoles.size());
    assertTrue(userRoles.stream().anyMatch(re -> RolesEnum.BUYER == re));
    assertTrue(userRoles.stream().anyMatch(re -> RolesEnum.SUPPLIER == re));
  }

}
