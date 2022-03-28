package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UnmergedJaggaerUserException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UserRolesConflictException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.RolePermissionInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserResponseDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SSOCodeData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SSOCodeData.SSOCode;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 *
 */
@SpringBootTest(
    classes = {ProfileManagementService.class, ConclaveAPIConfig.class, JaggaerAPIConfig.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(ConclaveAPIConfig.class)
class ProfileManagementServiceTest {

  private static final String USERID = "john.smith@example.com";
  private static final String ROLEKEY_BUYER = "JAEGGER_BUYER";
  private static final String ROLEKEY_SUPPLIER = "JAEGGER_SUPPLIER";
  private static final RolePermissionInfo ROLE_PERMISSION_INFO_BUYER =
      new RolePermissionInfo().roleKey(ROLEKEY_BUYER);
  private static final RolePermissionInfo ROLE_PERMISSION_INFO_SUPPLIER =
      new RolePermissionInfo().roleKey(ROLEKEY_SUPPLIER);

  private static final SSOCodeData SSO_CODE_DATA = SSOCodeData.builder().ssoCode(Set.of(
      SSOCode.builder().ssoCodeValue(JaggaerAPIConfig.SSO_CODE_VALUE).ssoUserLogin(USERID).build()))
      .build();

  @MockBean
  private ConclaveService conclaveService;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean
  private JaggaerService jaggaerService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @MockBean
  private JaggaerSOAPService jaggaerSOAPService;

  @Autowired
  private ProfileManagementService profileManagementService;

  /*
   * CON-1680-AC1(a)
   */
  @Test
  void testGetUserRolesConclaveBuyerJaggaerBuyer() {

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserByEmail(USERID))
        .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(1, userRoles.size());
    assertEquals(RolesEnum.BUYER, userRoles.get(0));
  }

  /*
   * CON-1680-AC1(b)
   */
  @Test
  void testGetUserRolesConclaveSupplierJaggaerSupplier() {

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID).detail(
        new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveSupplierData(USERID))
        .thenReturn(Optional.of(ReturnCompanyData.builder()
            .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build()).build()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(1, userRoles.size());
    assertEquals(RolesEnum.SUPPLIER, userRoles.get(0));
  }

  /*
   * CON-1680-AC2(a)
   */
  @Test
  void testGetUserRolesNoConclaveUserThrowsResourceNotFound() {

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.empty());

    var ex = assertThrows(ResourceNotFoundException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals("User [" + USERID + "] not found in Conclave", ex.getMessage());
  }

  /*
   * CON-1680-AC2(b)
   */
  @Test
  void testGetUserRolesNoJaggaerUserThrowsResourceNotFound() {

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID)).thenReturn(Optional.empty());
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

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));

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

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID).detail(
        new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
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

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID)).thenReturn(Optional.empty());
    when(userProfileService.resolveSupplierData(USERID))
        .thenReturn(Optional.of(ReturnCompanyData.builder()
            .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build()).build()));

    var ex = assertThrows(UserRolesConflictException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals(
        "User [" + USERID
            + "] has conflicting Conclave/Jaggaer roles (Conclave: [buyer], Jaggaer: [supplier])",
        ex.getMessage());
  }

  /*
   * CON-1680-AC7
   */
  @Test
  void testGetUserRolesConclaveBothJaggaerBoth() {

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(
            List.of(ROLE_PERMISSION_INFO_BUYER, ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserByEmail(USERID))
        .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));
    when(userProfileService.resolveSupplierData(USERID))
        .thenReturn(Optional.of(ReturnCompanyData.builder()
            .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build()).build()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(2, userRoles.size());
    assertTrue(userRoles.stream().anyMatch(re -> RolesEnum.BUYER == re));
    assertTrue(userRoles.stream().anyMatch(re -> RolesEnum.SUPPLIER == re));
  }

  /*
   * CON-1680-AC8(a)
   */
  @Test
  void testGetUserRolesConclaveBuyerJaggaerBuyerUnmerged() {

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID)
        .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveBuyerUserByEmail(USERID))
        .thenReturn(Optional.of(SubUser.builder().build()));

    var ex = assertThrows(UnmergedJaggaerUserException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals("User [" + USERID + "] is not merged in Jaggaer (no SSO data)", ex.getMessage());
  }

  /*
   * CON-1680-AC8(b)
   */
  @Test
  void testGetUserRolesConclaveSupplierJaggaerSupplierSuperUserUnmerged() {

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID).detail(
        new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveSupplierData(USERID)).thenReturn(Optional
        .of(ReturnCompanyData.builder().returnCompanyInfo(CompanyInfo.builder().build()).build()));

    var ex = assertThrows(UnmergedJaggaerUserException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals("User [" + USERID + "] is not merged in Jaggaer (no SSO data)", ex.getMessage());
  }

  /*
   * CON-1680-AC8(c)
   */
  @Test
  void testGetUserRolesConclaveSupplierJaggaerSupplierSubUserUnmerged() {

    var userProfileResponseInfo = new UserProfileResponseInfo().userName(USERID).detail(
        new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(userProfileService.resolveSupplierData(USERID))
        .thenReturn(Optional.of(ReturnCompanyData.builder()
            .returnSubUser(SubUsers.builder().subUsers(Set.of(SubUser.builder().build())).build())
            .build()));

    var ex = assertThrows(UnmergedJaggaerUserException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals("User [" + USERID + "] is not merged in Jaggaer (no SSO data)", ex.getMessage());
  }

  /*
   * CON-1682-AC1 (a)
   */
  @Test
  @Disabled
  void testRegisterUserUpdateJaggaerBuyerOrgExisted() {

    var registerUserResponse = profileManagementService.registerUser(USERID);

    assertEquals(RegisterUserResponse.UserActionEnum.EXISTED, registerUserResponse.getUserAction());
    assertEquals(RegisterUserResponse.UserActionEnum.EXISTED, registerUserResponse.getUserAction());

  }

}
