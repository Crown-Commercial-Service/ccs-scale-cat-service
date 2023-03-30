package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasText;
import static uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum.BUYER;
import static uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum.SUPPLIER;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import joptsimple.internal.Strings;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.UserRegistrationNotificationConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.LoginDirectorEdgeCaseException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UserRolesConflictException;
import uk.gov.crowncommercial.dts.scale.cat.model.SchemeType;
import uk.gov.crowncommercial.dts.scale.cat.model.SupplierLink;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.GroupAccessRole;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.RolePermissionInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserContactInfoList;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BuyerUserDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.GetUserResponse.RolesEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse.OrganisationActionEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RegisterUserResponse.UserActionEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.User;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateCompanyRequest.CreateUpdateCompanyRequestBuilder;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SSOCodeData.SSOCode;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

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
  static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

  private final ConclaveService conclaveService;
  private final ConclaveAPIConfig conclaveAPIConfig;
  private final UserProfileService userProfileService;
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final JaggaerService jaggaerService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final NotificationService notificationService;
  private final UserRegistrationNotificationConfig userRegistrationNotificationConfig;
  private final BuyerUserDetailsRepo buyerDetailsRepo;
  private final EncryptionService encryptionService;
  private final TenderDBSupplierLinkService supplierLinkService;

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

    var conclaveUserOrg = conclaveService.getOrganisationProfile(conclaveUser.getOrganisationId())
        .orElseThrow(() -> new ResourceNotFoundException(
            format(ERR_MSG_FMT_CONCLAVE_USER_ORG_MISSING, conclaveUser.getOrganisationId())));

    var conclaveRoles = new HashSet<RolesEnum>();
    populateConclaveRoles(conclaveRoles, conclaveUser);
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_CONCLAVE, userId, conclaveRoles));

    var jaggaerRoles = new HashSet<RolesEnum>();
    getJaggaerRolesWithSSoUserData(jaggaerRoles, userId,
        conclaveService.getOrganisationIdentifer(conclaveUserOrg));
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_JAGGAER, userId, jaggaerRoles));

    if (jaggaerRoles.isEmpty()) {
      // CON-1680-AC2
      throw new ResourceNotFoundException(format(ERR_MSG_FMT_JAGGAER_USER_MISSING, userId));
    }

    if (conclaveRoles.size() == 1
        && !jaggaerRoles.contains(conclaveRoles.stream().findFirst().orElseThrow())) {
      // CON-1680-AC5&6
      throw new UserRolesConflictException(
          format(ERR_MSG_FMT_ROLES_CONFLICT, userId, conclaveRoles, jaggaerRoles));
    }

    // CON-1680-AC1&7
    return List.copyOf(conclaveRoles);
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
    var conclaveUserOrg = conclaveService.getOrganisationProfile(conclaveUser.getOrganisationId())
        .orElseThrow(() -> new ResourceNotFoundException(
            format(ERR_MSG_FMT_CONCLAVE_USER_ORG_MISSING, conclaveUser.getOrganisationId())));

    var conclaveRoles = new HashSet<RolesEnum>();
    populateConclaveRoles(conclaveRoles, conclaveUser);
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_CONCLAVE, userId, conclaveRoles));

    var jaggaerRoles = new HashSet<RolesEnum>();
    var jaggaerUserData = populateJaggaerRoles(jaggaerRoles, userId,
        conclaveService.getOrganisationIdentifer(conclaveUserOrg));
    log.debug(format(MSG_FMT_SYS_ROLES, SYSID_JAGGAER, userId, jaggaerRoles));

    if (conclaveRoles.size() == 1 && jaggaerRoles.size() == 1
        && !Objects.equals(conclaveRoles, jaggaerRoles)) {
      // CON-1682-AC13&14
      throw new UserRolesConflictException(
          format(ERR_MSG_FMT_ROLES_CONFLICT, userId, conclaveRoles, jaggaerRoles));
    }

    // Get the conclave user /contacts (inc email, tel etc)
    var conclaveUserContacts = conclaveService.getUserContacts(userId);
    var registerUserResponse = new RegisterUserResponse();
    var createUpdateCompanyDataBuilder = CreateUpdateCompanyRequest.builder();
    var returnRoles = new ArrayList<RegisterUserResponse.RolesEnum>();
    if (conclaveRoles.contains(BUYER) && jaggaerRoles.size() == 1 && jaggaerRoles.contains(BUYER)) {

      // CON-1682-AC1&17(buyer): Update Jaggaer Buyer
      updateBuyer(conclaveUser, conclaveUserOrg, conclaveUserContacts,
          createUpdateCompanyDataBuilder, jaggaerUserData.getFirst(), registerUserResponse);
      returnRoles.add(RegisterUserResponse.RolesEnum.BUYER);

    } else if (conclaveRoles.size() == 1 && conclaveRoles.contains(BUYER)
        && jaggaerRoles.isEmpty()) {

      // CON-1682-AC2: Create Jaggaer Buyer
      createBuyer(conclaveUser, conclaveUserOrg, conclaveUserContacts,
          createUpdateCompanyDataBuilder, registerUserResponse);
      returnRoles.add(RegisterUserResponse.RolesEnum.BUYER);
      sendUserRegistrationNotification(conclaveUser, conclaveUserOrg);
      saveBuyerDetails(conclaveUser.getUserName());

    } else if (conclaveRoles.containsAll(Set.of(BUYER, SUPPLIER)) && jaggaerRoles.isEmpty()) {

      // CON-1682-AC15: Create Jaggaer Buyer / return temp error code
      createBuyer(conclaveUser, conclaveUserOrg, conclaveUserContacts,
          createUpdateCompanyDataBuilder, registerUserResponse);
      sendUserRegistrationNotification(conclaveUser, conclaveUserOrg);
      saveBuyerDetails(conclaveUser.getUserName());

      throw new LoginDirectorEdgeCaseException("CON1682-AC15: Dual Conclave roles, buyer created");

    } else if (conclaveRoles.contains(SUPPLIER) && jaggaerRoles.size() == 1
        && jaggaerRoles.contains(SUPPLIER)) {

      // CON-1682-AC8&17(supplier): Update Jaggaer Supplier
      var jaggaerSupplierData = jaggaerUserData.getSecond().orElseThrow();
      updateSupplier(conclaveUser, conclaveUserOrg, conclaveUserContacts,
          createUpdateCompanyDataBuilder, jaggaerSupplierData, registerUserResponse);
      returnRoles.add(RegisterUserResponse.RolesEnum.SUPPLIER);

    } else if (conclaveRoles.contains(SUPPLIER) && jaggaerRoles.isEmpty()) {

      var primaryOrgId = conclaveService.getOrganisationIdentifer(conclaveUserOrg);
      
      // Validate DUNS-Number
      if (!conclaveUserOrg.getIdentifier().getScheme().equalsIgnoreCase("US-DUN")) {
        var validDunsNumber = conclaveUserOrg.getAdditionalIdentifiers().stream()
            .filter(dnumber -> dnumber.getScheme().equalsIgnoreCase("US-DUN")).findAny();
        if (validDunsNumber.isPresent()) {
          conclaveUserOrg.setIdentifier(validDunsNumber.get());
        } else {
          sendSupplierRegistrationInvalidDunsNotification(conclaveUserOrg);
        }
      }

      // Now searched by org (legal) identifer (SCHEME-ID)
      var orgMapping =
          retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(primaryOrgId);

      // Now searched by Cas Organisation Id
      if (orgMapping.isEmpty()) {
        orgMapping =
            retryableTendersDBDelegate.findOrganisationMappingByCasOrganisationId(primaryOrgId);
      }
      
      // Now searched by Supplier mapping table CAS-665
      if (orgMapping.isEmpty()) {
        try {
          var schemeType = TendersAPIModelUtils.validateSchemeType()
              .get(conclaveUserOrg.getIdentifier().getScheme());
          var supplierLink =
              supplierLinkService.get(schemeType, conclaveUserOrg.getIdentifier().getId());
          if (Objects.nonNull(supplierLink)) {
            // populate org-mapping table
            orgMapping = Optional.of(retryableTendersDBDelegate.save(OrganisationMapping.builder()
                .organisationId(primaryOrgId).externalOrganisationId(supplierLink.getBravoId())
                .casOrganisationId(primaryOrgId).createdAt(Instant.now()).createdBy("SupplierLink")
                .build()));
          }
        } catch (Exception e) {
          log.error("Error while seraching supplier mapping table", e);
        }
      }

      if (orgMapping.isPresent()) {
        var jaggaerSupplierOrgId = orgMapping.get().getExternalOrganisationId();
        
        // CON-1682-AC10: Create Jaggaer Supplier sub-user
        createUpdateSupplierSubUser(String.valueOf(jaggaerSupplierOrgId), createUpdateCompanyDataBuilder,
            conclaveUser, conclaveUserOrg, conclaveUserContacts, userId, registerUserResponse);

      } else {
        // Call Jaggaer to check the supplier super user is exists
        var superUser =
            userProfileService.getSupplierDataByDUNSNumber(conclaveUserOrg.getIdentifier().getId());
        if (superUser.isPresent()) {
          // Create Jaggaer Supplier sub-user
          createUpdateSupplierSubUser(superUser.get().getReturnCompanyInfo().getBravoId(),
              createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg, conclaveUserContacts,
              userId, registerUserResponse);
          
          retryableTendersDBDelegate.save(OrganisationMapping.builder()
              .organisationId(primaryOrgId)
              .externalOrganisationId(
                  Integer.parseInt(superUser.get().getReturnCompanyInfo().getBravoId()))
              .createdAt(Instant.now()).createdBy(conclaveUser.getUserName()).build());
          
        } else {
          // CON-1682-AC9: Supplier is first user in company - create as super-user
          createUpdateSuperUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
              conclaveUserContacts, CompanyType.SELLER, Optional.empty());
          var createUpdateCompanyRequest = createUpdateCompanyDataBuilder.build();
          log.debug("Creating supplier super-user company: [{}]", createUpdateCompanyRequest);

          var createUpdateCompanyResponse =
              jaggaerService.createUpdateCompany(createUpdateCompanyRequest);

          retryableTendersDBDelegate.save(OrganisationMapping.builder()
              .organisationId(primaryOrgId)
              .externalOrganisationId(createUpdateCompanyResponse.getBravoId())
              .createdAt(Instant.now()).createdBy(conclaveUser.getUserName()).build());

          registerUserResponse.userAction(UserActionEnum.CREATED);
          registerUserResponse.organisationAction(OrganisationActionEnum.CREATED);
        }
      }
      returnRoles.add(RegisterUserResponse.RolesEnum.SUPPLIER);

    } else {
      throw new UserRolesConflictException(format(ERR_MSG_FMT_NO_ROLES, userId));
    }

    registerUserResponse.roles(returnRoles);
    return registerUserResponse;
  }
  
  private void createUpdateSupplierSubUser(final String jaggaerSupplierOrgId,
      final CreateUpdateCompanyRequestBuilder createUpdateCompanyDataBuilder,
      final UserProfileResponseInfo conclaveUser, OrganisationProfileResponseInfo conclaveUserOrg,
      final UserContactInfoList conclaveUserContacts, final String userId,
      final RegisterUserResponse registerUserResponse) {

    // CON-1682-AC10: Create Jaggaer Supplier sub-user
    createUpdateSubUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
        conclaveUserContacts, Optional.empty(), jaggaerSupplierOrgId,
        jaggaerAPIConfig.getDefaultSupplierRightsProfile());
    log.debug("Creating supplier sub-user: [{}]", userId);
    jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());
    
    registerUserResponse.userAction(UserActionEnum.CREATED);
    registerUserResponse.organisationAction(OrganisationActionEnum.EXISTED);
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
    if (ssoCodeDataExists(jaggaerSupplierData.getReturnCompanyInfo().getSsoCodeData())
        && Objects.equals(conclaveUser.getUserName(), jaggaerSupplierData.getReturnCompanyInfo()
            .getSsoCodeData().getSsoCode().stream().findFirst().orElseThrow().getSsoUserLogin())) {

      // CON-1682-AC8: Update supplier super-user
      createUpdateSuperUserHelper(createUpdateCompanyDataBuilder, conclaveUser, conclaveUserOrg,
          conclaveUserContacts, CompanyType.SELLER,
          Optional.of(jaggaerSupplierData.getReturnCompanyInfo()));
      log.debug("Updating supplier super-user company: [{}]", conclaveUser.getUserName());
      jaggaerService.createUpdateCompany(createUpdateCompanyDataBuilder.build());
    } else {
      var jaggaerSupplierSubUser = jaggaerSupplierData.getReturnSubUser().getSubUsers().stream()
          .filter(subUser -> ssoCodeDataExists(subUser.getSsoCodeData())
              && Objects.equals(conclaveUser.getUserName(), subUser.getSsoCodeData().getSsoCode()
                  .stream().findFirst().orElseThrow().getSsoUserLogin()))
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
    } else {
      subUsersBuilder.operationCode(OperationCode.CREATE);
      // TODO: This keyword ensures the sub-user's division is set to the super-user's
      // userDivisionId
      subUserBuilder.division("Division");
      subUserBuilder.ssoCodeData(buildSSOCodeData(conclaveUser.getUserName()));
    }
    
    var conclaveRoles = new HashSet<RolesEnum>();
    populateConclaveRoles(conclaveRoles, conclaveUser);
    var isBuyer = conclaveRoles.contains(RolesEnum.BUYER);

    // TODO - mobilePhoneNumber requires '+' in front of it - include?
    // TODO - timezone hardcoded
    // TODO - phone number hardcoded in case of null
    createUpdateCompanyRequestBuilder
        .company(CreateUpdateCompany.builder().operationCode(OperationCode.UPDATE)
            .companyInfo(CompanyInfo.builder().bravoId(jaggaerOrgId)
                .extCode(isBuyer ? null : conclaveService.getOrganisationIdentifer(conclaveUserOrg))
                .extUniqueCode(isBuyer ? null : conclaveUserOrg.getIdentifier().getId()).build())
            .build())
        .subUsers(
            subUsersBuilder
                .subUsers(Set.of(subUserBuilder.name(conclaveUser.getFirstName())
                    .surName(conclaveUser.getLastName()).login(conclaveUser.getUserName())
                    .email(conclaveUser.getUserName()).rightsProfile(rightsProfile)
                    .phoneNumber(
                        Optional.ofNullable(userPersonalContacts.getPhone()).orElse("07123456789"))
                    .language("en_GB").timezoneCode("Europe/London").timezone("UTC").build()))
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
          .ssoCodeData(buildSSOCodeData(conclaveUser.getUserName()));
    }

    // Jaggaer will only accept ISO country codes (e.g. GB)
    parseCountryCode(conclaveUserOrg.getAddress().getCountryCode())
        .ifPresent(companyInfoBuilder::isoCountry);

    companyInfoBuilder.companyName(conclaveUserOrg.getIdentifier().getLegalName())
        .extCode(conclaveService.getOrganisationIdentifer(conclaveUserOrg)).type(companyType)
        .extUniqueCode(conclaveUserOrg.getIdentifier().getId()).type(companyType)
        .dAndBCode(conclaveUserOrg.getIdentifier().getId())
        .bizEmail("conclave@orgcontacts.todo.com").bizPhone("012345678").bizFax("012345678")
        .webSite("https://conclave-org-contacts.todo.com")
        .address(conclaveUserOrg.getAddress().getStreetAddress())
        .city(conclaveUserOrg.getAddress().getLocality())
        // TODO - Jaggaer rejects .province(conclaveUserOrg.getAddress().getRegion())
        .zip(conclaveUserOrg.getAddress().getPostalCode()).userName(conclaveUser.getFirstName())
        .userSurName(conclaveUser.getLastName()).userEmail(conclaveUser.getUserName())
        .userPhone(userPersonalContacts.getPhone());

    createUpdateCompanyRequestBuilder
        .company(createUpdateCompanyBuilder.companyInfo(companyInfoBuilder.build()).build());
  }

  private void populateConclaveRoles(final Set<RolesEnum> conclaveRoles,
      final UserProfileResponseInfo conclaveUser) {
    var conclaveBuyerSupplierRoleKeys = Map.of(conclaveAPIConfig.getBuyerRoleKey(), BUYER,
        conclaveAPIConfig.getCatUserRoleKey(), BUYER,
        conclaveAPIConfig.getSupplierRoleKey(), SUPPLIER);

    conclaveUser.getDetail().getRolePermissionInfo().stream().map(RolePermissionInfo::getRoleKey)
        .filter(conclaveBuyerSupplierRoleKeys::containsKey)
        .forEach(rk -> conclaveRoles.add(conclaveBuyerSupplierRoleKeys.get(rk)));
    
    //CAS-1048
    if (Objects.nonNull(conclaveUser.getDetail().getUserGroups()))
      conclaveUser.getDetail().getUserGroups().stream().map(GroupAccessRole::getAccessRole)
          .filter(conclaveBuyerSupplierRoleKeys::containsKey)
          .forEach(rk -> conclaveRoles.add(conclaveBuyerSupplierRoleKeys.get(rk)));
  }

  private Pair<Optional<SubUser>, Optional<ReturnCompanyData>> populateJaggaerRoles(
      final Set<RolesEnum> jaggaerRoles, final String userId, final String organisationIdentifier) {

    // SSO verification built-in to search
    var buyerSubUser = userProfileService.resolveBuyerUserBySSOUserLogin(userId);

    // If cache missing, refresh in case user has since been registered (by separate instance)
    if (buyerSubUser.isEmpty()) {
      userProfileService.refreshBuyerCache(userId);
      buyerSubUser = userProfileService.resolveBuyerUserBySSOUserLogin(userId);
      log.debug("Refreshed buyer user cache for [{}], now found? - [{}]", userId,
          buyerSubUser.isPresent());
    }

    buyerSubUser.ifPresent(su -> jaggaerRoles.add(BUYER));

    // SSO verification required
    var optSupplierCompanyData =
        userProfileService.resolveSupplierData(userId, organisationIdentifier);

    if (optSupplierCompanyData.isPresent()) {
      var supplierCompanyData = optSupplierCompanyData.get();

      // Explicit SSO verification required
      var expectedSSOData = buildSSOCodeData(userId);

      // Check the super-user and sub-user for matching SSO
      if (Objects.equals(expectedSSOData,
          supplierCompanyData.getReturnCompanyInfo().getSsoCodeData())
          || supplierCompanyData.getReturnSubUser().getSubUsers() != null
              && supplierCompanyData.getReturnSubUser().getSubUsers().stream()
                  .anyMatch(subUser -> Objects.equals(expectedSSOData, subUser.getSsoCodeData()))) {
        jaggaerRoles.add(SUPPLIER);
        return Pair.of(buyerSubUser, optSupplierCompanyData);
      }
    }
    return Pair.of(buyerSubUser, Optional.empty());
  }

  private Pair<Optional<SubUser>, Optional<ReturnCompanyData>> getJaggaerRolesWithSSoUserData(
          final Set<RolesEnum> jaggaerRoles, final String userId, final String organisationIdentifier) {

    // SSO verification built-in to search
    var buyerSubUser = userProfileService.resolveBuyerUserBySSOUserLogin(userId);

    // If cache missing, refresh in case user has since been registered (by separate instance)
    if (buyerSubUser.isEmpty()) {
      userProfileService.refreshBuyerCache(userId);
      buyerSubUser = userProfileService.resolveBuyerUserBySSOUserLogin(userId);
      log.debug("Refreshed buyer user cache for [{}], now found? - [{}]", userId,
              buyerSubUser.isPresent());
    }

    if(buyerSubUser.isPresent()&& null!=buyerSubUser.get().getSsoCodeData()&&userId.equalsIgnoreCase(
            buyerSubUser.get().getSsoCodeData().getSsoCode().stream().findFirst().get().getSsoUserLogin())){
      buyerSubUser.ifPresent(su -> jaggaerRoles.add(BUYER));
    }
    // SSO verification required
    var optSupplierCompanyData =
            userProfileService.resolveSupplierData(userId, organisationIdentifier);

    if (optSupplierCompanyData.isPresent()) {
      var supplierCompanyData = optSupplierCompanyData.get();

      // Explicit SSO verification required
      var expectedSSOData = buildSSOCodeData(userId);

      // Check the super-user and sub-user for matching SSO
      if (Objects.equals(expectedSSOData,
              supplierCompanyData.getReturnCompanyInfo().getSsoCodeData())
              || supplierCompanyData.getReturnSubUser().getSubUsers() != null
              && supplierCompanyData.getReturnSubUser().getSubUsers().stream()
              .anyMatch(subUser -> Objects.equals(expectedSSOData, subUser.getSsoCodeData()))) {
        jaggaerRoles.add(SUPPLIER);
        return Pair.of(buyerSubUser, optSupplierCompanyData);
      }
    }
    return Pair.of(buyerSubUser, Optional.empty());

  }

  private void sendUserRegistrationNotification(final UserProfileResponseInfo conclaveUser,
      final OrganisationProfileResponseInfo conclaveUserOrg) {

    var placeholders = Map.of("org_name", conclaveUserOrg.getIdentifier().getLegalName(),
        "first_name", conclaveUser.getFirstName(), "last_name", conclaveUser.getLastName(), "email",
        conclaveUser.getUserName());

    notificationService.sendEmail(userRegistrationNotificationConfig.getTemplateId(),
        userRegistrationNotificationConfig.getTargetEmail(), placeholders, "");
  }
  
  private void sendSupplierRegistrationInvalidDunsNotification(
      final OrganisationProfileResponseInfo conclaveUserOrg) {

    var placeholders = Map.of("org_name", conclaveUserOrg.getIdentifier().getLegalName(),
        "org_scheme", conclaveUserOrg.getIdentifier().getScheme(), "org_number",
        conclaveUserOrg.getIdentifier().getId());

    notificationService.sendEmail(userRegistrationNotificationConfig.getInvalidDunsTemplateId(),
        userRegistrationNotificationConfig.getTargetEmail(), placeholders, "");
  }

  private BuyerUserDetails saveBuyerDetails(final String profile) {
    var userProfile = userProfileService.resolveBuyerUserProfile(profile)
        .orElseThrow(() -> new ResourceNotFoundException(ERR_MSG_FMT_JAGGAER_USER_MISSING));
    return buyerDetailsRepo.save(BuyerUserDetails.builder().userId(userProfile.getUserId())
        .userPassword(encryptionService.generateBuyerPassword()).exported(Boolean.FALSE)
        .createdAt(Instant.now()).createdBy("ProfileManagement").build());
  }

  static SSOCodeData buildSSOCodeData(final String userId) {
    return SSOCodeData.builder().ssoCode(Set.of(SSOCode.builder()
        .ssoCodeValue(JaggaerAPIConfig.SSO_CODE_VALUE).ssoUserLogin(userId).build())).build();
  }

  static boolean ssoCodeDataExists(final SSOCodeData ssoCodeData) {
    return ssoCodeData != null && ssoCodeData.getSsoCode() != null
        && !ssoCodeData.getSsoCode().isEmpty();
  }

  static Optional<String> parseCountryCode(final String countryCode) {
    if (hasText(countryCode) && ISO_COUNTRIES.contains(countryCode)) {
      return Optional.of(countryCode);
    }
    if (hasText(countryCode) && countryCode.contains("-")) {
      var countryCodePrefix = countryCode.substring(0, countryCode.indexOf('-'));
      if (ISO_COUNTRIES.contains(countryCodePrefix)) {
        return Optional.of(countryCodePrefix);
      }
    }
    return Optional.empty();
  }

  public List<User> getOrgUsers(String principal) {

    Assert.hasLength(principal, "principal must not be empty");
    List<uk.gov.crowncommercial.dts.scale.cat.model.generated.User> userList=new ArrayList<>();

    var conclaveUser = conclaveService.getUserProfile(principal).orElseThrow(
            () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, principal)));
    var conclaveUserOrg = conclaveService.getOrganisationProfile(conclaveUser.getOrganisationId())
            .orElseThrow(() -> new ResourceNotFoundException(
                    format(ERR_MSG_FMT_CONCLAVE_USER_ORG_MISSING, conclaveUser.getOrganisationId())));


    var optSupplierCompanyData =
            userProfileService.resolveCompanyProfileData(conclaveService.getOrganisationIdentifer(conclaveUserOrg));

    if(optSupplierCompanyData.isPresent()){

      CompanyInfo companyInfo = optSupplierCompanyData.get().getReturnCompanyInfo();

      if( CollectionUtils.isNotEmpty(optSupplierCompanyData.get().getReturnSubUser().getSubUsers())){
        optSupplierCompanyData.get().getReturnSubUser().getSubUsers().forEach(subUser -> {
          if(Objects.nonNull(subUser.getSsoCodeData())){

            Optional<SSOCode> ssoCodeOptional=subUser.getSsoCodeData().getSsoCode().stream().findFirst();
            if(ssoCodeOptional.isPresent() &&  !Objects.isNull(ssoCodeOptional.get().getSsoUserLogin()) && !Strings.isNullOrEmpty(subUser.getRightsProfile())){
              uk.gov.crowncommercial.dts.scale.cat.model.generated.User user= new uk.gov.crowncommercial.dts.scale.cat.model.generated.User();

              user.setUserId(ssoCodeOptional.get().getSsoUserLogin());
               user.setName(String.join(" ",subUser.getName(),subUser.getSurName()));
               user.addRolesItem(deriveRoleItem(companyInfo.getType()));
               userList.add(user);
            }
          }
        });
      }
    }
    return userList;
  }

  private User.RolesEnum deriveRoleItem(CompanyType companyType) {
      if(Objects.nonNull(companyType)){
        if(companyType.equals(CompanyType.BUYER)){
          return User.RolesEnum.BUYER;
        }else if (companyType.equals(CompanyType.SELLER)){
          return User.RolesEnum.SUPPLIER;
        }
      }
      return null;
  }
}
