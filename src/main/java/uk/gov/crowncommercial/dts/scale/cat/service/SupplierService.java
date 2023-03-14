package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.springframework.util.CollectionUtils.isEmpty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ScoreAndCommentNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.EvaluationCommentList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.OperatorUser;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ParameterList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ScoreParameter;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ScoringRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ScoringTechEnvelope;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Section;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SectionList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.EnvelopeType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SupplierIdentification;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SupplierScore;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SupplierScoreList;
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
  private static final String RPA_DELIMITER = "~|";
  private static final String RPA_OPEN_ENVELOPE_ERROR_DESC = "Technical button not found";
  private static final String RPA_EVALUATE_ERROR_DESC = "Evaluate Tab button not found";
  public static final String JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";
  public static final String ORG_MAPPING_NOT_FOUND = "Organisation mapping not found";
  public static final String SECTION_NAME = "Tender";
  public static final String SECTION_POS = "1";
  public static final String PARAM_POS = "1";


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
        retryableTendersDBDelegate.findOrganisationMappingByCasOrganisationIdIn(supplierOrgIds);
    if (isEmpty(supplierOrgMappings)) {
      log.warn("No supplier org mappings found in Tenders DB for CA: '{}', Lot: '{}'", agreementId,
          lotId);
      return Collections.emptySet();
    }

    return supplierOrgMappings;

  }



  /**
   * Update supplier score in Jaggaer
   * 
   * @param profile
   * @param projectId
   * @param eventId
   * @param scoreAndComments
   * @return status
   */
  public String updateSupplierScores(final String profile, final Integer projectId,
      final String eventId, final List<ScoreAndCommentNonOCDS> scoreAndComments, boolean scoringComplete) {
    log.info("Calling updateSupplierScoreAndComment for {}", eventId);
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var buyerUser = userService.resolveBuyerUserProfile(profile)
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND));
    
    if (scoringComplete) {
      jaggaerService.completeTechnical(procurementEvent, buyerUser.getUserId());
      return "Successfully completed scores";
    }
    
    var scoreAndCommentMap = new HashMap<String, ScoreAndCommentNonOCDS>();
    for (ScoreAndCommentNonOCDS scoreAndComment : scoreAndComments) {
      scoreAndCommentMap.put(scoreAndComment.getOrganisationId(), scoreAndComment);
    }

    var validSuppliers = getValidSuppliers(procurementEvent, scoreAndComments
        .stream().map(ScoreAndCommentNonOCDS::getOrganisationId).toList());
    
   var supplierScoresList =  new ArrayList<SupplierScore>();

    for (Supplier supplier : validSuppliers.getFirst()) {
      var orgId = validSuppliers.getSecond().stream()
          .filter(org -> org.getExternalOrganisationId().equals(Integer.valueOf(supplier.getId())))
          .findFirst();
      if (orgId.isPresent()) {
        var scoreAndCommentNonOCDS = scoreAndCommentMap.get(orgId.get().getCasOrganisationId());
        var supplierScore = SupplierScore.builder()
            .supplierIdentification(SupplierIdentification.builder().id(supplier.getId()).build())
            .scoringTechEnvelope(ScoringTechEnvelope.builder().sectionList(SectionList.builder()
                .section(Arrays.asList(Section.builder().name(SECTION_NAME).sectionPos(SECTION_POS)
                    .parameterList(ParameterList.builder()
                        .parameter(Arrays.asList(ScoreParameter.builder().paramPos(PARAM_POS)
                            .score(scoreAndCommentNonOCDS.getScore().toString()).build()))
                        .build())
                    .build()))
                .build()).build()).build();
        
        supplierScoresList.add(supplierScore);
      }
    }

    var scoringRequest = ScoringRequest.builder().rfxReferenceCode(procurementEvent.getExternalReferenceId())
        .operatorUser(OperatorUser.builder().login(profile).build())
        .supplierScoreList(SupplierScoreList.builder().supplierScore(supplierScoresList).build()).build();
    
    log.info("Scoring Request {}", scoringRequest);
    var scoreResponse = jaggaerService.createUpdateScores(scoringRequest);
    
    if (scoreResponse.getReturnCode() != 0) {
      jaggaerService.startEvaluationAndOpenEnvelope(procurementEvent,
          buyerUser.getUserId());
      jaggaerService.createUpdateScores(scoringRequest);
    }
    
    retryableTendersDBDelegate.updateEventDate(procurementEvent, profile);
    return "Successfully updated scores";
  }


  public Collection<ScoreAndCommentNonOCDS> getScoresForSuppliers(final Integer procId,
      final String eventId) {
    var componentFilter = "EVAL_SUPPLIER_ENVELOPE_COMMENTS==ALL;OFFERS";
    var procurementEvent = validationService.validateProjectAndEventIds(procId, eventId);
    var exportRfxResponse = jaggaerService.getRfxByComponent(procurementEvent.getExternalEventId(),
        new HashSet<>(Arrays.asList(componentFilter)));
    return exportRfxResponse.getOffersList().getOffer().stream()
        .map(e -> new ScoreAndCommentNonOCDS()
            .organisationId(retryableTendersDBDelegate
                .findOrganisationMappingByExternalOrganisationId(e.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException(ORG_MAPPING_NOT_FOUND))
                .getCasOrganisationId())
            .score(e.getTechPoints()).comment(
                extractComment(e.getSupplierId(), exportRfxResponse.getEvaluationCommentList())))
        .toList();
  }

  private String extractComment(Integer supplierId, EvaluationCommentList evaluationCommentList) {
    String defaultComment = "No comment found";
    if (Objects.nonNull(evaluationCommentList.getEnvelopeSupplierCommentList())) {
      Optional<String> comment = evaluationCommentList.getEnvelopeSupplierCommentList()
          .getEnvelopeSupplierComment().stream().filter(e -> e.getSupplierId().equals(supplierId))
          .map(e -> e.getCommentData().stream().findAny().get().getComment()).findAny();
      defaultComment = comment.isPresent() ? comment.get() : defaultComment;
    }
    return defaultComment;
  }

  public Pair<List<Supplier>, Set<OrganisationMapping>> getValidSuppliers(
          final ProcurementEvent procurementEvent, final List<String> orgIds) {
    var supplierOrgIds = orgIds.stream().collect(Collectors.toSet());
    // Retrieve and verify Tenders DB org mappings
    var supplierOrgMappings =
            retryableTendersDBDelegate.findOrganisationMappingByCasOrganisationIdIn(supplierOrgIds);
    if (isEmpty(supplierOrgMappings)) {
      var errorDesc =
              String.format("No supplier organisation mappings found in Tenders DB %s", supplierOrgIds);
      log.error(errorDesc);
      throw new JaggaerRPAException(errorDesc);
    }

    // Find all externalOrgIds
    var supplierExternalIds = supplierOrgMappings.stream()
            .map(OrganisationMapping::getExternalOrganisationId).collect(Collectors.toSet());

    // Find actual suppliers of project and event
    var suppliers = jaggaerService.getRfxWithSuppliers(procurementEvent.getExternalEventId()).getSuppliersList()
            .getSupplier();

    // Find all unmatched org ids
    var unMatchedSuppliers = supplierExternalIds.stream()
            .filter(orgid -> suppliers.stream()
                    .noneMatch(supplier -> supplier.getCompanyData().getId().equals(orgid)))
            .toList();

    if (!isEmpty(unMatchedSuppliers)) {
      var errorDesc =
              String.format("Supplied organisation mappings not matched with actual suppliers '%s'",
                      unMatchedSuppliers);
      log.error(errorDesc);
      throw new SupplierNotMatchException(errorDesc);
    }

    // Comparing the requested organisation ids and event suppliers info
    var matchedSuppliers = suppliers.stream()
            .filter(supplier -> supplierExternalIds.stream()
                    .anyMatch(orgId -> orgId.equals(supplier.getCompanyData().getId())))
            .toList();

    return Pair.of(
            matchedSuppliers.stream()
                    .map(e -> Supplier.builder().companyData(e.getCompanyData())
                            .id(String.valueOf(e.getCompanyData().getId())).build())
                    .toList(),
            supplierOrgMappings);
  }
}
