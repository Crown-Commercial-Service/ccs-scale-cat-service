package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.springframework.util.CollectionUtils.isEmpty;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ScoreAndCommentNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.EnvelopeType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessNameEnum;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * Encapsulates operations related to suppliers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final AgreementsService agreementsService;
  private final ValidationService validationService;
  private final UserProfileService userService;
  private final JaggaerService jaggaerService;
  private final RPAGenericService rpaGenericService;
  private static final String RPA_DELIMITER = "~|";
  private static final String RPA_OPEN_ENVELOPE_ERROR_DESC = "Technical button not found";
  private static final String RPA_EVALUATE_ERROR_DESC = "Evaluate Tab button not found";
  public static final String JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";

  /**
   * Retrieves suppliers from the Agreements Service based on the CA and Lot and resolves each to a
   * Jaggaer {@link Supplier} object via the organisation mapping table.
   *
   * @param agreementId
   * @param lotId
   * @return the list of suppliers for the given CA/Lot
   * @throws AgreementsServiceApplicationException if no suppliers are found in AS for given CA/Lot
   * @throws TendersDBDataException if not supplier org mappings are found in the mapping table
   */
  public List<Supplier> resolveSuppliers(final String agreementId, final String lotId) {

    var supplierOrgMappings = getSuppliersForLot(agreementId, lotId);

    var supplierExternalIds = supplierOrgMappings.stream()
        .map(OrganisationMapping::getExternalOrganisationId).collect(Collectors.toSet());

    return supplierExternalIds.stream()
        .map(id -> Supplier.builder().companyData((CompanyData.builder().id(id)).build()).build())
        .collect(Collectors.toList());
  }

  /**
   * Get OrganisationMapping objects matching Suppliers on a given Lot in the Agreements Service.
   *
   * @param agreementId
   * @param lotId
   * @return
   */
  public Set<OrganisationMapping> getSuppliersForLot(final String agreementId, final String lotId) {

    // Retrieve and verify AS suppliers
    var lotSuppliers = agreementsService.getLotSuppliers(agreementId, lotId);
    if (isEmpty(lotSuppliers)) {
      log.warn("No Lot Suppliers found in AS for CA: '{}', Lot: '{}'", agreementId, lotId);
      return Collections.emptySet();
    }

    var supplierOrgIds =
        lotSuppliers.stream().map(ls -> ls.getOrganization().getId()).collect(Collectors.toSet());

    // Retrieve and verify Tenders DB org mappings
    var supplierOrgMappings =
        retryableTendersDBDelegate.findOrganisationMappingByOrganisationIdIn(supplierOrgIds);
    if (isEmpty(supplierOrgMappings)) {
      log.warn("No supplier org mappings found in Tenders DB for CA: '{}', Lot: '{}'", agreementId,
          lotId);
      return Collections.emptySet();
    }

    return supplierOrgMappings;

  }

  /**
   * Update supplier score and comments in Jaggaer
   * 
   * @param profile
   * @param projectId
   * @param eventId
   * @param scoreAndComments
   * @return status
   */
  public String updateSupplierScoreAndComment(final String profile, final Integer projectId,
      final String eventId, final List<ScoreAndCommentNonOCDS> scoreAndComments) {
    log.info("Calling updateSupplierScoreAndComment for {}", eventId);
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var buyerUser = userService.resolveBuyerUserProfile(profile)
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND));
    var scoreAndCommentMap = new HashMap<String, ScoreAndCommentNonOCDS>();
    for (ScoreAndCommentNonOCDS scoreAndComment : scoreAndComments) {
      scoreAndCommentMap.put(scoreAndComment.getOrganisationId(), scoreAndComment);
    }

    var validSuppliers = rpaGenericService.getValidSuppliers(procurementEvent, scoreAndComments
        .stream().map(ScoreAndCommentNonOCDS::getOrganisationId).collect(Collectors.toList()));
    var appendSupplierList = new StringJoiner(RPA_DELIMITER);
    var appendScoreList = new StringJoiner(RPA_DELIMITER);
    var appendCommentList = new StringJoiner(RPA_DELIMITER);

    for (Supplier supplier : validSuppliers.getFirst()) {
      var orgId = validSuppliers.getSecond().stream()
          .filter(org -> org.getExternalOrganisationId().equals(supplier.getCompanyData().getId()))
          .findFirst();
      if (orgId.isPresent()) {
        var scoreAndCommentNonOCDS = scoreAndCommentMap.get(orgId.get().getOrganisationId());
        appendSupplierList.add(supplier.getCompanyData().getName());
        appendScoreList.add(scoreAndCommentNonOCDS.getScore().toString());
        appendCommentList.add(scoreAndCommentNonOCDS.getComment());
      }
    }

    // input details
    var suppliers = appendSupplierList.toString();
    var comments = appendCommentList.toString();
    var scores = appendScoreList.toString();

    log.info("Supplier Names: {}", suppliers);
    log.info("Supplier comments: {}", comments);
    log.info("Supplier scores: {}", scores);

    var buyerEncryptedPwd = rpaGenericService.getBuyerEncryptedPassword(buyerUser.getUserId());
    // Creating RPA process input string
    var inputBuilder = RPAProcessInput.builder().userName(buyerUser.getEmail())
        .password(buyerEncryptedPwd).ittCode(procurementEvent.getExternalReferenceId())
        .score(scores).comment(comments).supplierName(suppliers);
    try {
      return rpaGenericService.callRPAMessageAPI(inputBuilder.build(),
          RPAProcessNameEnum.ASSIGN_SCORE);
    } catch (JaggaerRPAException je) {
      // Start evaluation event
      if (je.getMessage().contains(RPA_EVALUATE_ERROR_DESC)) {
        jaggaerService.startEvaluationAndOpenEnvelope(procurementEvent, buyerUser.getUserId());
        return rpaGenericService.callRPAMessageAPI(inputBuilder.build(),
            RPAProcessNameEnum.ASSIGN_SCORE);
      }
      // Open envelope event
      else if (je.getMessage().contains(RPA_OPEN_ENVELOPE_ERROR_DESC)) {
        jaggaerService.openEnvelope(procurementEvent, buyerUser.getUserId(), EnvelopeType.TECH);
        return rpaGenericService.callRPAMessageAPI(inputBuilder.build(),
            RPAProcessNameEnum.ASSIGN_SCORE);
      } else
        throw je;
    }
  }
}
