package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.lang.String.format;
import static uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum.BUYER;
import static uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum.SUPPLIER;
import java.time.Instant;
import java.util.*;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.LoginDirectorEdgeCaseException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
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
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SSOCodeData.SSOCode;
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

  /**
   * Gets a user's roles (buyer or supplier) from both the ID management system (Conclave) and the
   * eSourcing platform (Jaggaer - based on the presence of SSO data), compares them and returns the
   * matched roles or throws an exception in case of a mismatch.
   *
   * @param userId
   * @return a list of roles
   * @throws ResourceNotFoundException if the user does not exist in either system or their org
   *         cannot be found
   * @throws UserRolesConflictException if the user has a role in ID management that they do not
   *         have in Jaggaer
   */
  public List<RolesEnum> getUserRoles(final String userId) {
    Assert.hasLength(userId, "userId must not be empty");
    var conclaveUser = conclaveService.getUserProfile(userId).orElseThrow(
        () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, userId)));

    var conclaveUserOrg = conclaveService.getOrganisation(conclaveUser.getOrganisationId())
        .orElseThrow(() -> new ResourceNotFoundException(
            format(ERR_MSG_FMT_CONCLAVE_USER_ORG_MISSING, conclaveUser.getOrganisationId())));

    var sysRoles = newSysRolesMappings();
    populateConclaveRoles(sysRoles, conclaveUser);
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_CONCLAVE, userId, sysRoles.get(SYSID_CONCLAVE)));

    populateJaggaerRoles(sysRoles, userId,
        conclaveService.getOrganisationIdentifer(conclaveUserOrg));
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_JAGGAER, userId, sysRoles.get(SYSID_JAGGAER)));

    if (sysRoles.get(SYSID_JAGGAER).isEmpty()) {
      // CON-1680-AC2
      throw new ResourceNotFoundException(format(ERR_MSG_FMT_JAGGAER_USER_MISSING, userId));
    }

    if (sysRoles.get(SYSID_CONCLAVE).size() == 1 && !sysRoles.get(SYSID_JAGGAER)
        .contains(sysRoles.get(SYSID_CONCLAVE).stream().findFirst().get())) {
      // CON-1680-AC5&6
      throw new UserRolesConflictException(format(ERR_MSG_FMT_ROLES_CONFLICT, userId,
          sysRoles.get(SYSID_CONCLAVE), sysRoles.get(SYSID_JAGGAER)));
    }

    // CON-1680-AC1&7
    return List.copyOf(sysRoles.get(SYSID_CONCLAVE));
  }

  /**
   * Registers, or updates, either Jaggaer buyer or supplier. Buyers are always sub-users of the
   * self-service 'org', suppliers have their own orgs and may be the super user or a sub user.
   *
   * @param userId
   * @return user and org actions taken
   */
  public RegisterUserResponse registerUser(final String userId) {

    Assert.hasLength(userId, "userId must not be empty");
    var conclaveUser = conclaveService.getUserProfile(userId).orElseThrow(
        () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, userId)));
    var conclaveUserOrg = conclaveService.getOrganisation(conclaveUser.getOrganisationId())
        .orElseThrow(() -> new ResourceNotFoundException(
            format(ERR_MSG_FMT_CONCLAVE_USER_ORG_MISSING, conclaveUser.getOrganisationId())));

    var sysRoles = newSysRolesMappings();
    populateConclaveRoles(sysRoles, conclaveUser);
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_CONCLAVE, userId, sysRoles.get(SYSID_CONCLAVE)));

    var jaggaerUserData = populateJaggaerRoles(sysRoles, userId,
        conclaveService.getOrganisationIdentifer(conclaveUserOrg));
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_JAGGAER, userId, sysRoles.get(SYSID_JAGGAER)));

    if (sysRoles.get(SYSID_CONCLAVE).size() == 1 && sysRoles.get(SYSID_JAGGAER).size() == 1
        && !Objects.equals(sysRoles.get(SYSID_CONCLAVE), sysRoles.get(SYSID_JAGGAER))) {
      // CON-1682-AC13&14
      throw new UserRolesConflictException(format(ERR_MSG_FMT_ROLES_CONFLICT, userId,
          sysRoles.get(SYSID_CONCLAVE), sysRoles.get(SYSID_JAGGAER)));
    }

    // Get the conclave user /contacts (inc email, tel etc)
    var conclaveUserContacts = conclaveService.getUserContacts(userId);
    var registerUserResponse = new RegisterUserResponse();
    var createUpdateCompanyDataBuilder = CreateUpdateCompanyRequest.builder();
    var returnRoles = new ArrayList<RegisterUserResponse.RolesEnum>();

    if (sysRoles.get(SYSID_CONCLAVE).contains(BUYER) && sysRoles.get(SYSID_JAGGAER).size() == 1
        && sysRoles.get(SYSID_JAGGAER).contains(BUYER)) {

      // CON-1682-AC1&17(buyer): Update Jaggaer Buyer
      updateBuyer(conclaveUser, conclaveUserOrg, conclaveUserContacts,
          createUpdateCompanyDataBuilder, jaggaerUserData.getFirst(), registerUserResponse);
      returnRoles.add(RegisterUserResponse.RolesEnum.BUYER);

    } else if (sysRoles.get(SYSID_CONCLAVE).size() == 1
        && sysRoles.get(SYSID_CONCLAVE).contains(BUYER) && !sysRoles.get(SYSID_JAGGAER).isEmpty()) {

      // CON-1682-AC2: Create Jaggaer Buyer
      createBuyer(conclaveUser, conclaveUserOrg, conclaveUserContacts,
          createUpdateCompanyDataBuilder, registerUserResponse);
      returnRoles.add(RegisterUserResponse.RolesEnum.BUYER);
      // TODO: Notify John G via email / Salesforce..??

    } else if (sysRoles.get(SYSID_CONCLAVE).containsAll(Set.of(BUYER, SUPPLIER))
        && sysRoles.get(SYSID_JAGGAER).isEmpty()) {

      // CON-1682-AC15: Create Jaggaer Buyer / return temp error code
      createBuyer(conclaveUser, conclaveUserOrg, conclaveUserContacts,
          createUpdateCompanyDataBuilder, registerUserResponse);
      throw new LoginDirectorEdgeCaseException("CON1682-AC15: Dual Conclave roles, buyer created");

    } else if (sysRoles.get(SYSID_CONCLAVE).contains(SUPPLIER)
        && sysRoles.get(SYSID_JAGGAER).size() == 1
        && sysRoles.get(SYSID_JAGGAER).contains(SUPPLIER)) {

      // CON-1682-AC8&17(supplier): Update Jaggaer Supplier
      var jaggaerSupplierData = jaggaerUserData.getSecond().orElseThrow();
      updateSupplier(conclaveUser, conclaveUserOrg, conclaveUserContacts,
          createUpdateCompanyDataBuilder, jaggaerSupplierData, registerUserResponse);
      returnRoles.add(RegisterUserResponse.RolesEnum.SUPPLIER);

    } else if (sysRoles.get(SYSID_CONCLAVE).contains(SUPPLIER)
        && !sysRoles.get(SYSID_JAGGAER).contains(SUPPLIER)) {

      // Now searched by org (legal) identifer (SCHEME-ID)
      var orgMapping = retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(
          conclaveService.getOrganisationIdentifer(conclaveUserOrg));

      if (orgMapping.isPresent()) {
        var jaggaerSupplierOrgId = orgMapping.get().getExternalOrganisationId();

        // CON-1682-AC10: Create Jaggaer Supplier sub-user
        createUpdateSubUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
            conclaveUserContacts, Optional.empty(), String.valueOf(jaggaerSupplierOrgId),
            jaggaerAPIConfig.getDefaultSupplierRightsProfile());
        log.debug("Creating supplier sub-user: [{}]", userId);
        jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());

        registerUserResponse.userAction(UserActionEnum.CREATED);
        registerUserResponse.organisationAction(OrganisationActionEnum.EXISTED);

      } else {
        // CON-1682-AC9: Supplier is first user in company - create as super-user
        createUpdateSuperUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
            conclaveUserContacts, CompanyType.SELLER, Optional.empty());
        var createUpdateCompanyRequest = createUpdateCompanyDataBuilder.build();
        log.debug("Creating supplier super-user company: [{}]", createUpdateCompanyRequest);

        var createUpdateCompanyResponse =
            jaggaerService.createUpdateCompany(createUpdateCompanyRequest);

        retryableTendersDBDelegate.save(OrganisationMapping.builder()
            .organisationId(conclaveService.getOrganisationIdentifer(conclaveUserOrg))
            .externalOrganisationId(createUpdateCompanyResponse.getBravoId())
            .createdAt(Instant.now()).createdBy(conclaveUser.getUserName()).build());

        registerUserResponse.userAction(UserActionEnum.CREATED);
        registerUserResponse.organisationAction(OrganisationActionEnum.CREATED);
      }
      returnRoles.add(RegisterUserResponse.RolesEnum.SUPPLIER);

    } else {
      throw new UserRolesConflictException(format(ERR_MSG_FMT_NO_ROLES, userId));
    }

    registerUserResponse.roles(returnRoles);
    return registerUserResponse;
  }

  private void createBuyer(final UserProfileResponseInfo conclaveUser,
      final OrganisationProfileResponseInfo conclaveUserOrg,
      final UserContactInfoList conclaveUserContacts,
      final CreateUpdateCompanyRequestBuilder createUpdateCompanyDataBuilder,
      final RegisterUserResponse registerUserResponse) {
    createUpdateSubUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
        conclaveUserContacts, Optional.empty(), jaggaerAPIConfig.getSelfServiceId(),
        jaggaerAPIConfig.getDefaultBuyerRightsProfile());

    log.debug("Creating buyer user: [{}], request: {}", conclaveUser.getUserName(),
        createUpdateCompanyDataBuilder.build());
    jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());

    userProfileService.refreshBuyerCache(conclaveUser.getUserName());
    registerUserResponse.userAction(UserActionEnum.CREATED);
    registerUserResponse.organisationAction(OrganisationActionEnum.PENDING);
  }

  private void updateBuyer(final UserProfileResponseInfo conclaveUser,
      final OrganisationProfileResponseInfo conclaveUserOrg,
      final UserContactInfoList conclaveUserContacts,
      final CreateUpdateCompanyRequestBuilder createUpdateCompanyDataBuilder,
      final Optional<SubUser> jaggaerBuyer, final RegisterUserResponse registerUserResponse) {

    createUpdateSubUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
        conclaveUserContacts, jaggaerBuyer, jaggaerAPIConfig.getSelfServiceId(),
        jaggaerAPIConfig.getDefaultBuyerRightsProfile());

    log.debug("Updating buyer user: [{}]", conclaveUser.getUserName());
    jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());
    userProfileService.refreshBuyerCache(conclaveUser.getUserName());

    registerUserResponse.userAction(UserActionEnum.EXISTED);
    registerUserResponse.organisationAction(OrganisationActionEnum.EXISTED);
  }

  private void updateSupplier(final UserProfileResponseInfo conclaveUser,
      final OrganisationProfileResponseInfo conclaveUserOrg,
      final UserContactInfoList conclaveUserContacts,
      final CreateUpdateCompanyRequestBuilder createUpdateCompanyDataBuilder,
      final ReturnCompanyData jaggaerSupplierData,
      final RegisterUserResponse registerUserResponse) {

    // Determine whether supplier represented by super or sub user
    if (jaggaerSupplierData.getReturnCompanyInfo().getSsoCodeData() != null
        && Objects.equals(conclaveUser.getUserName(),
            jaggaerSupplierData.getReturnCompanyInfo().getSsoCodeData().getSsoCode().stream()
                .findFirst().orElseGet(() -> SSOCode.builder().build()).getSsoUserLogin())) {

      // CON-1682-AC8: Update supplier super-user
      createUpdateSuperUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
          conclaveUserContacts, CompanyType.SELLER,
          Optional.of(jaggaerSupplierData.getReturnCompanyInfo()));
      log.debug("Updating supplier super-user company: [{}]", conclaveUser.getUserName());
      jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());
    } else {
      var jaggaerSupplierSubUser =
          jaggaerSupplierData.getReturnSubUser().getSubUsers().stream()
              .filter(
                  subUser -> Objects.equals(conclaveUser.getUserName(),
                      subUser.getSsoCodeData().getSsoCode().stream().findFirst()
                          .orElseGet(() -> SSOCode.builder().build()).getSsoUserLogin()))
              .findFirst();

      // CON-1682-AC16: Update supplier sub-user
      createUpdateSubUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
          conclaveUserContacts, jaggaerSupplierSubUser,
          jaggaerSupplierData.getReturnCompanyInfo().getBravoId(),
          jaggaerAPIConfig.getDefaultSupplierRightsProfile());

      log.debug("Updating supplier sub-user: [{}]", conclaveUser.getUserName());
      jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());

    }
    registerUserResponse.userAction(UserActionEnum.EXISTED);
    registerUserResponse.organisationAction(OrganisationActionEnum.EXISTED);
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
        // TODO - Fails whole op with err 135. Comment out for now until resolution agreed. Attempt
        // to set division and business unit (fails unless FK to current Division)

        // subUserBuilder.division(subUser.getDivision())
        // .businessUnit(conclaveUserOrg.getIdentifier().getLegalName());
      }
    } else {
      subUsersBuilder.operationCode(OperationCode.CREATE);
      // TODO - division + businessUnit hardcoded - API error without
      subUserBuilder.division("Central Government").businessUnit("Crown Commercial Service");
      subUserBuilder.ssoCodeData(SSOCodeData.builder()
          .ssoCode(Set.of(SSOCode.builder().ssoCodeValue(JaggaerAPIConfig.SSO_CODE_VALUE)
              .ssoUserLogin(conclaveUser.getUserName()).build()))
          .build());
    }

    // TODO - mobilePhoneNumber requires '+' in front of it - include?
    // TODO - timezone hardcoded
    // TODO - phone number hardcoded in case of null
    createUpdateCompanyRequestBuilder
        .company(CreateUpdateCompany.builder().operationCode(OperationCode.UPDATE)
            .companyInfo(CompanyInfo.builder().bravoId(jaggaerOrgId).build()).build())
        .subUsers(
            subUsersBuilder
                .subUsers(Set.of(subUserBuilder.name(conclaveUser.getFirstName())
                    .surName(conclaveUser.getLastName()).login(conclaveUser.getUserName())
                    .email(conclaveUser.getUserName()).rightsProfile(rightsProfile)
                    .phoneNumber(
                        Optional.ofNullable(userPersonalContacts.getPhone()).orElse("07123456789"))
                    .language("en_GB").timezoneCode("Europe/London").timezone("GMT").build()))
                .build());
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
          .ssoCodeData(SSOCodeData.builder()
              .ssoCode(Set.of(SSOCode.builder().ssoCodeValue(JaggaerAPIConfig.SSO_CODE_VALUE)
                  .ssoUserLogin(conclaveUser.getUserName()).build()))
              .build());
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

    createUpdateCompanyRequestBuilder
        .company(createUpdateCompanyBuilder.companyInfo(companyInfoBuilder.build()).build());
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
      final Map<String, Set<RolesEnum>> sysRoles, final String userId,
      final String organisationIdentifier) {

    // SSO verification built-in to search
    var buyerSubUser = userProfileService.resolveBuyerUserBySSOUserLogin(userId);
    buyerSubUser.ifPresent(su -> sysRoles.get(SYSID_JAGGAER).add(BUYER));

    // SSO verification required
    var optSupplierCompanyData =
        userProfileService.resolveSupplierData(userId, organisationIdentifier);

    if (optSupplierCompanyData.isPresent()) {
      var supplierCompanyData = optSupplierCompanyData.get();

      // Explicit SSO verification required
      var expectedSSOData =
          SSOCodeData
              .builder().ssoCode(Set.of(SSOCode.builder()
                  .ssoCodeValue(JaggaerAPIConfig.SSO_CODE_VALUE).ssoUserLogin(userId).build()))
              .build();

      // Check the super-user and sub-user for matching SSO
      if (Objects.equals(expectedSSOData,
          supplierCompanyData.getReturnCompanyInfo().getSsoCodeData())
          || supplierCompanyData.getReturnSubUser().getSubUsers() != null
              && supplierCompanyData.getReturnSubUser().getSubUsers().stream()
                  .anyMatch(subUser -> Objects.equals(expectedSSOData, subUser.getSsoCodeData()))) {
        sysRoles.get(SYSID_JAGGAER).add(SUPPLIER);
        return Pair.of(buyerSubUser, optSupplierCompanyData);
      }
    }
    return Pair.of(buyerSubUser, Optional.empty());

  }

  private Map<String, Set<RolesEnum>> newSysRolesMappings() {
    return Map.of(SYSID_CONCLAVE, new HashSet<RolesEnum>(), SYSID_JAGGAER,
        new HashSet<RolesEnum>());
  }

}
