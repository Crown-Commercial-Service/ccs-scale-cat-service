package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.lang.String.format;
import static uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum.BUYER;
import static uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum.SUPPLIER;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UnmergedJaggaerUserException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UserRolesConflictException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.RolePermissionInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserContactInfoList;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse.OrganisationActionEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse.UserActionEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateCompanyRequest.CreateUpdateCompanyRequestBuilder;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

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
  static final String ERR_MSG_FMT_CONCLAVE_USER_ORG_MISSING =
      "Organisation [%s] not found in Conclave";
  static final String ERR_MSG_FMT_JAGGAER_USER_MISSING = "User [%s] not found in Jaggaer";
  static final String ERR_MSG_FMT_NO_ROLES = "User [%s] is neither buyer NOR supplier in Conclave";
  static final String ERR_MSG_JAGGAER_INCONSISTENT = "User [%s] has inconsistent data in Jaggaer";
  static final String ERR_MSG_JAGGAER_USER_UNMERGED =
      "User [%s] is not merged in Jaggaer (no SSO data)";
  static final String MSG_FMT_SYS_ROLES = "%s user [%s] has roles %s";
  static final String MSG_FMT_BOTH_ROLES = "User [%s] is both buyer AND supplier in Conclave";

  private final ConclaveService conclaveService;
  private final ConclaveAPIConfig conclaveAPIConfig;
  private final UserProfileService userProfileService;
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final JaggaerService jaggaerService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  private final JaggaerSOAPService jaggaerSOAPService;

  public List<RolesEnum> getUserRoles(final String userId) {
    Assert.hasLength(userId, "userId must not be empty");
    var conclaveUser = conclaveService.getUserProfile(userId)
        // CON-1680-AC2(a)
        .orElseThrow(
            () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, userId)));

    var sysRoles = newSysRolesMappings();
    populateConclaveRoles(sysRoles, conclaveUser);
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_CONCLAVE, userId, sysRoles.get(SYSID_CONCLAVE)));
    validateBuyerSupplierConclaveRoles(sysRoles.get(SYSID_CONCLAVE), userId);
    var jaggaerUserData = populateJaggaerRoles(sysRoles, userId);
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_JAGGAER, userId, sysRoles.get(SYSID_JAGGAER)));

    if (sysRoles.get(SYSID_JAGGAER).isEmpty()) {
      // CON-1680-AC2(b)
      throw new ResourceNotFoundException(format(ERR_MSG_FMT_JAGGAER_USER_MISSING, userId));
    }

    if (!Objects.equals(sysRoles.get(SYSID_CONCLAVE), sysRoles.get(SYSID_JAGGAER))) {
      // CON-1680-AC5&6
      throw new UserRolesConflictException(format(ERR_MSG_FMT_ROLES_CONFLICT, userId,
          sysRoles.get(SYSID_CONCLAVE), sysRoles.get(SYSID_JAGGAER)));
    }

    verifyUserMerged(sysRoles, jaggaerUserData, userId);

    // CON-1680-AC1
    return List.copyOf(sysRoles.get(SYSID_CONCLAVE));
  }

  /*
   * CON-1680-AC8
   */
  private void verifyUserMerged(final Map<String, Set<RolesEnum>> sysRoles,
      final Pair<Optional<SubUser>, Optional<ReturnCompanyData>> jaggaerUserData,
      final String userId) {

    var expectedSSOData = SSOCodeData.builder().ssoCodeValue(JaggaerAPIConfig.SSO_CODE_VALUE)
        .ssoUserLogin(userId).build();

    // Self-serve Buyer sub-user
    if (sysRoles.get(SYSID_JAGGAER).contains(BUYER)) {
      var jaggaerBuyerSubUser = jaggaerUserData.getFirst().orElseThrow();
      compareSSOData(expectedSSOData, jaggaerBuyerSubUser.getSsoCodeData(), userId);
    }

    if (sysRoles.get(SYSID_JAGGAER).contains(SUPPLIER)) {
      var jaggaerSupplierData = jaggaerUserData.getSecond().orElseThrow();

      // Supplier sub-user
      if (jaggaerSupplierData.getReturnSubUser() != null
          && jaggaerSupplierData.getReturnSubUser().getSubUsers().size() == 1) {
        var supplierSubUser =
            jaggaerSupplierData.getReturnSubUser().getSubUsers().stream().findFirst().orElseThrow();
        compareSSOData(expectedSSOData, supplierSubUser.getSsoCodeData(), userId);
      } else {
        // Supplier super-user
        compareSSOData(expectedSSOData, jaggaerSupplierData.getReturnCompanyInfo().getSsoCodeData(),
            userId);
      }
    }
  }

  private void compareSSOData(final SSOCodeData expected, final SSOCodeData actual,
      final String userId) {
    if (!Objects.equals(expected, actual)) {
      throw new UnmergedJaggaerUserException(format(ERR_MSG_JAGGAER_USER_UNMERGED, userId));
    }
  }

  public RegisterUserResponse registerUser(final String userId) {

    /*
     * Get the conclave user (inc. roles)
     *
     * TODO - question - can we just use the roles in the incoming JWT (look for buyer/supplier)?
     * Save a call to conclave? Update - this was being discussed in a meeting 23-11-2021
     */
    Assert.hasLength(userId, "userId must not be empty");
    var conclaveUser = conclaveService.getUserProfile(userId).orElseThrow(
        () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, userId)));

    var sysRoles = newSysRolesMappings();
    populateConclaveRoles(sysRoles, conclaveUser);
    validateBuyerSupplierConclaveRoles(sysRoles.get(SYSID_CONCLAVE), userId);

    var jaggaerUserData = populateJaggaerRoles(sysRoles, userId);

    // If same number of roles, but different - 409 conflict with no update.
    if (sysRoles.get(SYSID_CONCLAVE).size() == sysRoles.get(SYSID_JAGGAER).size()
        && !Objects.equals(sysRoles.get(SYSID_CONCLAVE), sysRoles.get(SYSID_JAGGAER))) {
      // CON-1682-AC13+AC14
      throw new UserRolesConflictException(format(ERR_MSG_FMT_ROLES_CONFLICT, userId,
          sysRoles.get(SYSID_CONCLAVE), sysRoles.get(SYSID_JAGGAER)));
    }

    // Get the conclave user /contacts (inc email, tel etc)
    var conclaveUserContacts = conclaveService.getUserContacts(userId);
    var conclaveUserOrg = conclaveService.getOrganisation(conclaveUser.getOrganisationId())
        .orElseThrow(() -> new ResourceNotFoundException(
            format(ERR_MSG_FMT_CONCLAVE_USER_ORG_MISSING, conclaveUser.getOrganisationId())));
    var registerUserResponse = new RegisterUserResponse();
    var createUpdateCompanyDataBuilder = CreateUpdateCompanyRequest.builder();

    if (sysRoles.get(SYSID_CONCLAVE).contains(BUYER)
        && sysRoles.get(SYSID_JAGGAER).contains(BUYER)) {

      // CON-1682-AC1: Update Jaggaer Buyer
      createUpdateSubUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
          conclaveUserContacts, jaggaerUserData.getFirst(), jaggaerAPIConfig.getSelfServiceId(),
          jaggaerAPIConfig.getDefaultBuyerRightsProfile());

      log.debug("Updating buyer user: [{}]", userId);
      jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());

      registerUserResponse.userAction(UserActionEnum.EXISTED);

      var division = jaggaerUserData.getFirst().get().getDivision();
      var orgAction = StringUtils.hasText(division) ? OrganisationActionEnum.EXISTED
          : OrganisationActionEnum.PENDING;
      registerUserResponse.organisationAction(orgAction);

    } else if (sysRoles.get(SYSID_CONCLAVE).contains(BUYER)
        && !sysRoles.get(SYSID_JAGGAER).contains(BUYER)) {

      // CON-1682-AC2: Create Jaggaer Buyer
      createUpdateSubUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
          conclaveUserContacts, Optional.empty(), jaggaerAPIConfig.getSelfServiceId(),
          jaggaerAPIConfig.getDefaultBuyerRightsProfile());

      log.debug("Creating buyer user: [{}], request: {}", userId,
          createUpdateCompanyDataBuilder.build());
      jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());

      // Temporary - SOAP API workaround for adding SSO data
      jaggaerSOAPService.updateSubUserSSO(
          UpdateSubUserSSO.builder().companyBravoID(jaggaerAPIConfig.getSelfServiceId())
              .subUserLogin(userId).subUserSSOLogin(userId).build());

      registerUserResponse.userAction(UserActionEnum.CREATED);
      registerUserResponse.organisationAction(OrganisationActionEnum.PENDING);

      // TODO: Notify John G via email / Salesforce..??

    } else if (sysRoles.get(SYSID_CONCLAVE).contains(SUPPLIER)
        && !sysRoles.get(SYSID_JAGGAER).contains(SUPPLIER)) {

      var orgMapping = retryableTendersDBDelegate
          .findOrganisationMappingByOrgId(conclaveUser.getOrganisationId());

      if (orgMapping.isPresent()) {
        var jaggaerSupplierOrgId = orgMapping.get().getExternalOrganisationId();

        // CON-1682-AC10: Create Jaggaer Supplier sub-user
        createUpdateSubUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
            conclaveUserContacts, Optional.empty(), String.valueOf(jaggaerSupplierOrgId),
            jaggaerAPIConfig.getDefaultSupplierRightsProfile());
        log.debug("Creating supplier sub-user: [{}]", userId);
        jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());

        // Temporary - SOAP API workaround for adding SSO data
        jaggaerSOAPService.updateSubUserSSO(
            UpdateSubUserSSO.builder().companyBravoID(String.valueOf(jaggaerSupplierOrgId))
                .subUserLogin(userId).subUserSSOLogin(userId).build());

        registerUserResponse.userAction(UserActionEnum.CREATED);
        registerUserResponse.organisationAction(OrganisationActionEnum.PENDING);

      } else {
        // CON-1682-AC9: Supplier is first user in company - create as super-user
        createUpdateSuperUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
            conclaveUserContacts, CompanyType.SELLER, Optional.empty());
        log.debug("Creating supplier super-user company: [{}]", userId);
        var createUpdateCompanyResponse =
            jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());

        // Temporary - SOAP API workaround for adding SSO data
        jaggaerSOAPService.updateSubUserSSO(UpdateSubUserSSO.builder()
            .companyBravoID(String.valueOf(createUpdateCompanyResponse.getBravoId()))
            .subUserLogin(userId).subUserSSOLogin(userId).build());

        retryableTendersDBDelegate
            .save(OrganisationMapping.builder().organisationId(conclaveUser.getOrganisationId())
                .externalOrganisationId(createUpdateCompanyResponse.getBravoId())
                .createdAt(Instant.now()).createdBy(conclaveUser.getUserName()).build());

        registerUserResponse.userAction(UserActionEnum.CREATED);
        registerUserResponse.organisationAction(OrganisationActionEnum.CREATED);
      }

    } else if (sysRoles.get(SYSID_CONCLAVE).contains(SUPPLIER)
        && sysRoles.get(SYSID_JAGGAER).contains(SUPPLIER)) {

      var jaggaerSupplierData = jaggaerUserData.getSecond().orElseThrow();

      if (jaggaerSupplierData.getReturnSubUser().getSubUsers().size() == 1) {
        // CON-1682-AC8(a): Update supplier sub-user
        createUpdateSubUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
            conclaveUserContacts,
            jaggaerSupplierData.getReturnSubUser().getSubUsers().stream().findFirst(),
            jaggaerSupplierData.getReturnCompanyInfo().getBravoId(),
            jaggaerAPIConfig.getDefaultSupplierRightsProfile());

        log.debug("Updating supplier sub-user: [{}]", userId);
        jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());

      } else if (jaggaerSupplierData.getReturnCompanyInfo() != null) {
        // CON-1682-AC8(b): Update supplier super-user
        createUpdateSuperUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
            conclaveUserContacts, CompanyType.SELLER,
            Optional.of(jaggaerSupplierData.getReturnCompanyInfo()));
        log.debug("Updating supplier super-user company: [{}]", userId);
        jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());

      } else {
        throw new UserRolesConflictException(format(ERR_MSG_JAGGAER_INCONSISTENT, userId));
      }
      registerUserResponse.userAction(UserActionEnum.EXISTED);
      registerUserResponse.organisationAction(OrganisationActionEnum.EXISTED);
    } else {
      throw new UserRolesConflictException(format(ERR_MSG_FMT_NO_ROLES, userId));
    }

    registerUserResponse.roles(sysRoles.get(SYSID_CONCLAVE).stream()
        .map(role -> RegisterUserResponse.RolesEnum.fromValue(role.getValue()))
        .collect(Collectors.toList()));
    return registerUserResponse;
  }

  private void validateBuyerSupplierConclaveRoles(final Collection<RolesEnum> roleKeys,
      final String userId) {
    if (roleKeys == null || roleKeys.isEmpty()) {
      // CON-1680-AC4
      // CON-1682-AC12
      throw new UserRolesConflictException(format(ERR_MSG_FMT_NO_ROLES, userId));
    }

    if (roleKeys.size() >= 2) {
      // CON-1680-AC7
      log.warn(format(MSG_FMT_BOTH_ROLES, userId));
    }
  }

  private void createUpdateSubUserHelper(
      final CreateUpdateCompanyRequestBuilder createUpdateCompanyRequestBuilder,
      final UserProfileResponseInfo conclaveUser,
      final OrganisationProfileResponseInfo conclaveUserOrg,
      final UserContactInfoList conclaveContacts, final Optional<SubUser> existingSubUser,
      final String jaggaerOrgId, final String rightsProfile) {

    var userPersonalContacts = conclaveService.extractUserPersonalContacts(conclaveContacts);
    var subUsersBuilder = SubUsers.builder();
    var subUserBuilder = SubUser.builder();

    if (existingSubUser.isPresent()) {

      subUsersBuilder.operationCode(OperationCode.UPDATE);

      var subUser = existingSubUser.orElseThrow();
      subUserBuilder.userId(subUser.getUserId());

      if (StringUtils.hasText(subUser.getDivision())) {
        // Attempt to set division and business unit (fails unless FK to current Division)
        subUserBuilder.division(subUser.getDivision())
            .businessUnit(conclaveUserOrg.getIdentifier().getLegalName());
      }
    } else {
      subUsersBuilder.operationCode(OperationCode.CREATE);
      // TODO - division + businessUnit hardcoded - API error without
      subUserBuilder.division("Central Government").businessUnit("Crown Commercial Service");
    }

    // TODO - mobilePhoneNumber requires '+' in front of it - include?
    // TODO - timezone hardcoded
    // TODO - phone number hardcoded in case of null
    createUpdateCompanyRequestBuilder
        .company(CreateUpdateCompany.builder().operationCode(OperationCode.UPDATE)
            .companyInfo(CompanyInfo.builder().bravoId(jaggaerOrgId).build()).build())
        .subUsers(subUsersBuilder.sendEMail("1").subUsers(Set.of(subUserBuilder
            .name(conclaveUser.getFirstName()).surName(conclaveUser.getLastName())
            .login(conclaveUser.getUserName()).email(conclaveUser.getUserName())
            .rightsProfile(rightsProfile)
            .phoneNumber(Optional.ofNullable(userPersonalContacts.getPhone()).orElse("07123456789"))
            .language("en_GB").timezoneCode("Europe/London").timezone("GMT").build())).build());
  }

  private void createUpdateSuperUserHelper(
      final CreateUpdateCompanyRequestBuilder createUpdateCompanyRequestBuilder,
      final UserProfileResponseInfo conclaveUser,
      final OrganisationProfileResponseInfo conclaveUserOrg,
      final UserContactInfoList conclaveContacts, final CompanyType companyType,
      final Optional<CompanyInfo> existingCompanyInfo) {

    var userPersonalContacts = conclaveService.extractUserPersonalContacts(conclaveContacts);

    // TODO - mobilePhoneNumber requires '+' in front of it - include?
    var companyInfoBuilder = CompanyInfo.builder();
    var createUpdateCompanyBuilder = CreateUpdateCompany.builder();

    if (existingCompanyInfo.isPresent()) {
      createUpdateCompanyBuilder.operationCode(OperationCode.UPDATE);
      companyInfoBuilder.bravoId(existingCompanyInfo.get().getBravoId());
    } else {
      createUpdateCompanyBuilder.operationCode(OperationCode.CREATE);
      companyInfoBuilder.userAlias(conclaveUser.getUserName())
          .ssoCodeData(SSOCodeData.builder().ssoCodeValue(JaggaerAPIConfig.SSO_CODE_VALUE)
              .ssoUserLogin(conclaveUser.getUserName()).build());
    }

    if (StringUtils.hasText(conclaveUserOrg.getAddress().getCountryCode())) {
      companyInfoBuilder.isoCountry(conclaveUserOrg.getAddress().getCountryCode());
    }

    companyInfoBuilder.companyName(conclaveUserOrg.getIdentifier().getLegalName())
        .extCode(conclaveUserOrg.getIdentifier().getId()) // TODO: Correct?
        .type(companyType).bizEmail("conclave@orgcontacts.todo.com").bizPhone("012345678")
        .bizFax("012345678").webSite("https://conclave-org-contacts.todo.com")
        .address(conclaveUserOrg.getAddress().getStreetAddress())
        .city(conclaveUserOrg.getAddress().getLocality())
        .province(conclaveUserOrg.getAddress().getRegion())
        .zip(conclaveUserOrg.getAddress().getPostalCode()).userName(conclaveUser.getFirstName())
        .userSurName(conclaveUser.getLastName()).userEmail(conclaveUser.getUserName())
        .userPhone(userPersonalContacts.getPhone());

    createUpdateCompanyRequestBuilder.company(
        createUpdateCompanyBuilder.sendEMail("1").companyInfo(companyInfoBuilder.build()).build());
  }

  private void populateConclaveRoles(final Map<String, Set<RolesEnum>> sysRoles,
      final UserProfileResponseInfo conclaveUser) {
    var conclaveBuyerSupplierRoleKeys = Map.of(conclaveAPIConfig.getBuyerRoleKey(), BUYER,
        conclaveAPIConfig.getSupplierRoleKey(), SUPPLIER);

    conclaveUser.getDetail().getRolePermissionInfo().stream().map(RolePermissionInfo::getRoleKey)
        .filter(conclaveBuyerSupplierRoleKeys::containsKey)
        .forEach(rk -> sysRoles.get(SYSID_CONCLAVE).add(conclaveBuyerSupplierRoleKeys.get(rk)));
  }

  private Pair<Optional<SubUser>, Optional<ReturnCompanyData>> populateJaggaerRoles(
      final Map<String, Set<RolesEnum>> sysRoles, final String userId) {

    var buyerSubUser = userProfileService.resolveBuyerUserByEmail(userId);
    buyerSubUser.ifPresent(su -> sysRoles.get(SYSID_JAGGAER).add(BUYER));

    var supplierCompanyData = userProfileService.resolveSupplierData(userId);
    supplierCompanyData.ifPresent(rcd -> sysRoles.get(SYSID_JAGGAER).add(SUPPLIER));

    return Pair.of(buyerSubUser, supplierCompanyData);
  }

  private Map<String, Set<RolesEnum>> newSysRolesMappings() {
    return Map.of(SYSID_CONCLAVE, new HashSet<RolesEnum>(), SYSID_JAGGAER,
        new HashSet<RolesEnum>());
  }

}
