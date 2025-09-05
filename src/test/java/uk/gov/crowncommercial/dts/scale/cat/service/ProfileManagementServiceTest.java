package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doNothing;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.UserRegistrationNotificationConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UserRolesConflictException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationAddressResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationIdentifier;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
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
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@ExtendWith(MockitoExtension.class)
class ProfileManagementServiceTest {

  private static final String USERID = "john.smith@example.com";
  private static final String ROLEKEY_BUYER = "JAEGGER_BUYER";
  private static final String ROLEKEY_SUPPLIER = "JAEGGER_SUPPLIER";
  private static final String ORG_SYS_ID = "0123456789";
  private static final String ORG_IDENTIFIER = "GB-COH-ABC123";
  private static final String COUNTRY_CODE = "GB";

  private static final RolePermissionInfo ROLE_PERMISSION_INFO_BUYER = new RolePermissionInfo();
  private static final RolePermissionInfo ROLE_PERMISSION_INFO_SUPPLIER = new RolePermissionInfo();

  static {
    ROLE_PERMISSION_INFO_BUYER.setRoleKey(ROLEKEY_BUYER);

    ROLE_PERMISSION_INFO_SUPPLIER.setRoleKey(ROLEKEY_SUPPLIER);
  }

  private static final SSOCodeData SSO_CODE_DATA = SSOCodeData.builder()
          .ssoCode(Set.of(SSOCode.builder().ssoCodeValue("OPEN_ID").ssoUserLogin(USERID).build()))
          .build();

  private static final OrganisationProfileResponseInfo ORG = new OrganisationProfileResponseInfo()
          .detail(new OrganisationDetail().organisationId(ORG_SYS_ID))
          .identifier(new OrganisationIdentifier().scheme("GB-COH").id(ORG_IDENTIFIER))
          .addAdditionalIdentifiersItem(new OrganisationIdentifier().scheme("US-DUN").id(ORG_IDENTIFIER))
          .address(new OrganisationAddressResponse().countryCode(COUNTRY_CODE));

  @Mock private ConclaveService conclaveService;
  @Mock private UserProfileService userProfileService;
  @Mock private WebclientWrapper webclientWrapper;
  @Mock private JaggaerService jaggaerService;
  @Mock private RetryableTendersDBDelegate retryableTendersDBDelegate;
  @Mock private NotificationService notificationService;
  @Mock private UserRegistrationNotificationConfig userRegistrationNotificationConfig;
  @InjectMocks private ProfileManagementService profileManagementService;
  @Mock private BuyerUserDetailsRepo buyerDetailsRepo;
  @Mock private TenderDBSupplierLinkService supplierLinkService;
  @Mock private ConclaveAPIConfig conclaveAPIConfig;
  @Mock private JaggaerAPIConfig jaggaerAPIConfig;

  private SSOCodeData ssoCodeData;

  @BeforeEach
  void setUp() {
    lenient().when(conclaveAPIConfig.getBuyerRoleKey()).thenReturn(ROLEKEY_BUYER);
    lenient().when(conclaveAPIConfig.getSupplierRoleKey()).thenReturn(ROLEKEY_SUPPLIER);
    lenient().when(conclaveAPIConfig.getCatUserRoleKey()).thenReturn("CAT_USER");

    ssoCodeData = SSOCodeData.builder()
            .ssoCode(Set.of(SSOCode.builder().ssoCodeValue("OPEN_ID").ssoUserLogin(USERID).build()))
            .build();
  }

  // ===== GET USER ROLES TESTS =====

  @Test
  void testGetUserRolesConclaveBuyerJaggaerBuyer() {
    var userProfileResponseInfo = new UserProfileResponseInfo()
            .userName(USERID)
            .organisationId(ORG_SYS_ID)
            .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisationProfile(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
            .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(1, userRoles.size());
    assertEquals(RolesEnum.BUYER, userRoles.get(0));
  }

  @Test
  void testGetUserRolesConclaveSupplierJaggaerSupplier() {
    var userProfileResponseInfo = new UserProfileResponseInfo()
            .userName(USERID)
            .organisationId(ORG_SYS_ID)
            .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisationProfile(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    when(userProfileService.resolveSupplierData(USERID, ORG_IDENTIFIER))
            .thenReturn(Optional.of(ReturnCompanyData.builder()
                    .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build())
                    .returnSubUser(SubUsers.builder().subUsers(Set.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build())).build())
                    .build()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(1, userRoles.size());
    assertEquals(RolesEnum.SUPPLIER, userRoles.get(0));
  }

  @Test
  void testGetUserRolesNoConclaveUserThrowsResourceNotFound() {
    var ex = assertThrows(ResourceNotFoundException.class,
            () -> profileManagementService.getUserRoles(USERID));
    assertEquals("User [" + USERID + "] not found in Conclave", ex.getMessage());
  }

  @Test
  void testGetUserRolesNoJaggaerUserThrowsResourceNotFound() {
    var userProfileResponseInfo = new UserProfileResponseInfo()
            .organisationId(ORG_SYS_ID)
            .userName(USERID)
            .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisationProfile(ORG_SYS_ID)).thenReturn(Optional.of(ORG));

    var ex = assertThrows(ResourceNotFoundException.class,
            () -> profileManagementService.getUserRoles(USERID));
    assertEquals("User [" + USERID + "] not found in Jaggaer", ex.getMessage());
  }

  @Test
  void testGetUserRolesConclaveSupplierJaggaerBuyer() {
    var userProfileResponseInfo = new UserProfileResponseInfo()
            .organisationId(ORG_SYS_ID)
            .userName(USERID)
            .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisationProfile(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
            .thenReturn(Optional.of(SubUser.builder()
                    .ssoCodeData(SSOCodeData.builder()
                            .ssoCode(Set.of(SSOCode.builder().ssoUserLogin(USERID).build())).build())
                    .build()));

    var ex = assertThrows(UserRolesConflictException.class,
            () -> profileManagementService.getUserRoles(USERID));
    assertTrue(ex.getMessage().contains("conflicting Conclave/Jaggaer roles"));
  }

  @Test
  void testGetUserRolesConclaveBuyerJaggaerSupplier() {
    var userProfileResponseInfo = new UserProfileResponseInfo()
            .organisationId(ORG_SYS_ID)
            .userName(USERID)
            .firstName("John")
            .lastName("Smith")
            .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisationProfile(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    // Mock that the user is NOT found as a buyer in Jaggaer (to create the conflict)
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
            .thenReturn(Optional.empty());
    // Mock the cache refresh method (it returns void, so we use doNothing)
    doNothing().when(userProfileService).refreshBuyerCache(USERID);
    // Mock that the user IS found as a supplier in Jaggaer (to create the conflict)
    when(userProfileService.resolveSupplierData(USERID, ORG_IDENTIFIER))
            .thenReturn(Optional.of(ReturnCompanyData.builder()
                    .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build())
                    .returnSubUser(SubUsers.builder().subUsers(Set.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build())).build())
                    .build()));
    // Mock for when organisationIdentifier is null (which can happen in some code paths)
    lenient().when(userProfileService.resolveSupplierData(USERID, null))
            .thenReturn(Optional.empty());

    var ex = assertThrows(UserRolesConflictException.class,
            () -> profileManagementService.getUserRoles(USERID));
    assertTrue(ex.getMessage().contains("conflicting Conclave/Jaggaer roles"));
  }

  @Test
  void testGetUserRolesConclaveBothJaggaerBoth() {
    var userProfileResponseInfo = new UserProfileResponseInfo()
            .organisationId(ORG_SYS_ID)
            .userName(USERID)
            .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER,
                    ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisationProfile(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
            .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));
    when(userProfileService.resolveSupplierData(USERID, ORG_IDENTIFIER))
            .thenReturn(Optional.of(ReturnCompanyData.builder()
                    .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build())
                    .returnSubUser(SubUsers.builder().subUsers(Set.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build())).build())
                    .build()));

    var userRoles = profileManagementService.getUserRoles(USERID);

    assertEquals(2, userRoles.size());
    assertTrue(userRoles.contains(RolesEnum.BUYER));
    assertTrue(userRoles.contains(RolesEnum.SUPPLIER));
  }

  // ===== REGISTER USER TESTS =====

  @Test
  void testRegisterUserUpdateJaggaerBuyer() {
    var userProfileResponseInfo = new UserProfileResponseInfo()
            .userName(USERID)
            .organisationId(ORG_SYS_ID)
            .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisationProfile(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
            .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));
    when(conclaveService.extractUserPersonalContacts(any()))
            .thenReturn(ConclaveService.UserContactPoints.builder().build());

    var registerUserResponse = profileManagementService.registerUser(USERID);

    assertEquals(RegisterUserResponse.UserActionEnum.EXISTED, registerUserResponse.getUserAction());
    assertEquals(List.of(RegisterUserResponse.RolesEnum.BUYER), registerUserResponse.getRoles());
  }

  @Test
  void testRegisterUserRolesConflictConclaveBuyerJaggaerSupplier() {
    var userProfileResponseInfo = new UserProfileResponseInfo()
            .organisationId(ORG_SYS_ID)
            .userName(USERID)
            .firstName("John")
            .lastName("Smith")
            .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_BUYER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisationProfile(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(conclaveService.getOrganisationIdentifer(ORG)).thenReturn(ORG_IDENTIFIER);
    // Mock that the user is NOT found as a buyer in Jaggaer (to create the conflict)
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
            .thenReturn(Optional.empty());
    // Mock the cache refresh method (it returns void, so we use doNothing)
    doNothing().when(userProfileService).refreshBuyerCache(USERID);
    // Mock that the user IS found as a supplier in Jaggaer (to create the conflict)
    when(userProfileService.resolveSupplierData(USERID, ORG_IDENTIFIER))
            .thenReturn(Optional.of(ReturnCompanyData.builder()
                    .returnCompanyInfo(CompanyInfo.builder().ssoCodeData(SSO_CODE_DATA).build())
                    .returnSubUser(SubUsers.builder().subUsers(Set.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build())).build())
                    .build()));
    // Mock for when organisationIdentifier is null (which can happen in some code paths)
    lenient().when(userProfileService.resolveSupplierData(USERID, null))
            .thenReturn(Optional.empty());
    // Mock for extractUserPersonalContacts to avoid NullPointerException
    lenient().when(conclaveService.extractUserPersonalContacts(any()))
            .thenReturn(ConclaveService.UserContactPoints.builder().build());

    var ex = assertThrows(UserRolesConflictException.class,
            () -> profileManagementService.registerUser(USERID));
    assertTrue(ex.getMessage().contains("conflicting Conclave/Jaggaer roles"));
  }

  @Test
  void testRegisterUserRolesConflictConclaveSupplierJaggaerBuyer() {
    var userProfileResponseInfo = new UserProfileResponseInfo()
            .organisationId(ORG_SYS_ID)
            .userName(USERID)
            .detail(new UserResponseDetail().rolePermissionInfo(List.of(ROLE_PERMISSION_INFO_SUPPLIER)));

    when(conclaveService.getUserProfile(USERID)).thenReturn(Optional.of(userProfileResponseInfo));
    when(conclaveService.getOrganisationProfile(ORG_SYS_ID)).thenReturn(Optional.of(ORG));
    when(userProfileService.resolveBuyerUserBySSOUserLogin(USERID))
            .thenReturn(Optional.of(SubUser.builder().ssoCodeData(SSO_CODE_DATA).build()));

    var ex = assertThrows(UserRolesConflictException.class,
            () -> profileManagementService.registerUser(USERID));
    assertTrue(ex.getMessage().contains("conflicting Conclave/Jaggaer roles"));
  }

}
