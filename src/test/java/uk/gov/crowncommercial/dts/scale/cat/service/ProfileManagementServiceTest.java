package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UserRolesConflictException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SSOCodeData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SSOCodeData.SSOCode;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService.UserContactPoints;

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
  private static final String ORG_SYS_ID = "0123456789";
  private static final String ORG_IDENTIFIER = "GB-COH-ABC123";
  private static final String COUNTRY_CODE = "GB";
  private static final RolePermissionInfo ROLE_PERMISSION_INFO_BUYER =
      new RolePermissionInfo().roleKey(ROLEKEY_BUYER);
  private static final RolePermissionInfo ROLE_PERMISSION_INFO_SUPPLIER =
      new RolePermissionInfo().roleKey(ROLEKEY_SUPPLIER);

  private static final SSOCodeData SSO_CODE_DATA = SSOCodeData.builder().ssoCode(Set.of(
      SSOCode.builder().ssoCodeValue(JaggaerAPIConfig.SSO_CODE_VALUE).ssoUserLogin(USERID).build()))
      .build();

  private static final OrganisationProfileResponseInfo ORG = new OrganisationProfileResponseInfo()
      .detail(new OrganisationDetail().organisationId(ORG_SYS_ID))
      .identifier(new OrganisationIdentifier().scheme("GB-COH").id(ORG_IDENTIFIER))
      .address(new OrganisationAddressResponse().countryCode(COUNTRY_CODE));

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

    var userProfileResponseInfo =
        new UserProfileResponseInfo().userName(USERID).organisationId(ORG_SYS_ID).detail(
            new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
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

    var userProfileResponseInfo =
        new UserProfileResponseInfo().userName(USERID).organisationId(ORG_SYS_ID).detail(
            new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    when(userProfileService.resolveSupplierData(USERID, ORG_IDENTIFIER))
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
    var ex = assertThrows(ResourceNotFoundException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals("User [" + USERID + "] not found in Conclave", ex.getMessage());
  }

  /*
   * CON-1680-AC2(b)
   */
  @Test
  void testGetUserRolesNoJaggaerUserThrowsResourceNotFound() {

    var userProfileResponseInfo =
        new UserProfileResponseInfo().organisationId(ORG_SYS_ID).userName(USERID).detail(
            new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));

    var ex = assertThrows(ResourceNotFoundException.class,
        () -> profileManagementService.getUserRoles(USERID));

    assertEquals("User [" + USERID + "] not found in Jaggaer", ex.getMessage());
  }

  /*
   * CON-1680-AC5
   */
  @Test
  void testGetUserRolesConclaveSupplierJaggaerBuyer() {

    var userProfileResponseInfo =
        new UserProfileResponseInfo().organisationId(ORG_SYS_ID).userName(USERID).detail(
            new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
        .thenReturn(Optional.of(SubUser.builder().build()));

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

    var userProfileResponseInfo =
        new UserProfileResponseInfo().organisationId(ORG_SYS_ID).userName(USERID).detail(
            new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    when(userProfileService.resolveSupplierData(USERID, ORG_IDENTIFIER))
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

    var userProfileResponseInfo = new UserProfileResponseInfo().organisationId(ORG_SYS_ID)
        .userName(USERID).detail(new UserResponseDetail().rolePermissionInfo(
            List.of(ROLE_PERMISSION_INFO_BUYER, ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
        .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));
    when(userProfileService.resolveSupplierData(USERID, ORG_IDENTIFIER))
        .thenReturn(Optional.of(ReturnCompanyData.builder()
            .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build()).build()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(2, userRoles.size());
    assertTrue(userRoles.stream().anyMatch(re -> RolesEnum.BUYER == re));
    assertTrue(userRoles.stream().anyMatch(re -> RolesEnum.SUPPLIER == re));
  }

  /*
   * CON-1682-AC1 (Buyer update)
   */
  @Test
  void testRegisterUserUpdateJaggaerBuyer() {

    var userProfileResponseInfo =
        new UserProfileResponseInfo().userName(USERID).organisationId(ORG_SYS_ID).detail(
            new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
        .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));
    when(conclaveService.extractUserPersonalContacts(any()))
        .thenReturn(UserContactPoints.builder().build());

    var registerUserResponse = profileManagementService.registerUser(USERID);

    assertEquals(RegisterUserResponse.UserActionEnum.EXISTED, registerUserResponse.getUserAction());
    assertEquals(RegisterUserResponse.UserActionEnum.EXISTED, registerUserResponse.getUserAction());
    assertEquals(List.of(RegisterUserResponse.RolesEnum.BUYER), registerUserResponse.getRoles());
  }

  /*
   * CON-1682-AC17 (Buyer update)
   */
  @Test
  void testRegisterUserUpdateJaggaerBuyerDualConclaveRoles() {

    var userProfileResponseInfo =
        new UserProfileResponseInfo().organisationId(ORG_SYS_ID).userName(USERID)
            .organisationId(ORG_SYS_ID).detail(new UserResponseDetail().rolePermissionInfo(
                List.of(ROLE_PERMISSION_INFO_BUYER, ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
        .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));
    when(conclaveService.extractUserPersonalContacts(any()))
        .thenReturn(UserContactPoints.builder().build());

    var registerUserResponse = profileManagementService.registerUser(USERID);

    assertEquals(RegisterUserResponse.UserActionEnum.EXISTED, registerUserResponse.getUserAction());
    assertEquals(RegisterUserResponse.UserActionEnum.EXISTED, registerUserResponse.getUserAction());
    assertEquals(List.of(RegisterUserResponse.RolesEnum.BUYER), registerUserResponse.getRoles());
  }

  /*
   * CON-1682-AC17 (Supplier update)
   */
  @Test
  void testRegisterUserUpdateJaggaerSupplierDualConclaveRoles() {

    var userProfileResponseInfo =
        new UserProfileResponseInfo().organisationId(ORG_SYS_ID).userName(USERID)
            .organisationId(ORG_SYS_ID).detail(new UserResponseDetail().rolePermissionInfo(
                List.of(ROLE_PERMISSION_INFO_BUYER, ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    when(userProfileService.resolveSupplierData(USERID, ORG_IDENTIFIER))
        .thenReturn(Optional.of(ReturnCompanyData.builder()
            .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build())
            // .returnSubUser(SubUsers.builder().subUsers(Set.of(null)).build()
            .build()));
    when(conclaveService.extractUserPersonalContacts(any()))
        .thenReturn(UserContactPoints.builder().build());

    var registerUserResponse = profileManagementService.registerUser(USERID);

    assertEquals(RegisterUserResponse.UserActionEnum.EXISTED, registerUserResponse.getUserAction());
    assertEquals(RegisterUserResponse.UserActionEnum.EXISTED, registerUserResponse.getUserAction());
    assertEquals(List.of(RegisterUserResponse.RolesEnum.SUPPLIER), registerUserResponse.getRoles());
  }

  /*
   * CON-1682 AC13
   */
  @Test
  void testRegisterUserRolesConflictConclaveSupplierJaggaerBuyer() {
    var userProfileResponseInfo =
        new UserProfileResponseInfo().organisationId(ORG_SYS_ID).userName(USERID).detail(
            new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
        .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));

    var ex = assertThrows(UserRolesConflictException.class,
        () -> profileManagementService.registerUser(USERID));

    assertEquals(
        "User [" + USERID
            + "] has conflicting Conclave/Jaggaer roles (Conclave: [supplier], Jaggaer: [buyer])",
        ex.getMessage());
  }

  /*
   * CON-1682 AC14
   */
  @Test
  void testRegisterUserRolesConflictConclaveBuyerJaggaerSupplier() {
    var userProfileResponseInfo =
        new UserProfileResponseInfo().organisationId(ORG_SYS_ID).userName(USERID).detail(
            new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisation(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    when(userProfileService.resolveSupplierData(USERID, ORG_IDENTIFIER))
        .thenReturn(Optional.of(ReturnCompanyData.builder()
            .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build()).build()));

    var ex = assertThrows(UserRolesConflictException.class,
        () -> profileManagementService.registerUser(USERID));

    assertEquals(
        "User [" + USERID
            + "] has conflicting Conclave/Jaggaer roles (Conclave: [buyer], Jaggaer: [supplier])",
        ex.getMessage());
  }

}
