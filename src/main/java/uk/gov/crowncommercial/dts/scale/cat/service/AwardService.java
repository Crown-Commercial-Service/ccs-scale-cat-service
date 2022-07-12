package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentsKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessNameEnum;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwardService {

  private final ValidationService validationService;
  private final UserProfileService userService;
  private final RPAGenericService rpaGenericService;
  private final DocumentTemplateResourceService documentTemplateResourceService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final JaggaerService jaggaerService;

  public static final String JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";
  public static final String SUPPLIERS_NOT_FOUND = "Supplier details not found";
  public static final String AWARDS_TO_MUTLIPLE_SUPPLIERS =
      "Awards to multiple suppliers is not currently supported";
  private static final String PRE_AWARD = "Pre-Award";
  private static final String EDIT_PRE_AWARD = "Edit Pre-Awarding";
  private static final String AWARD = "Award";
  private static final String RPA_END_EVALUATION_ERROR_DESC =
      "Awarding action dropdown button not found";
  static final String AWARDED_TEMPLATE_DESCRIPTION = "Awarded Templates";
  static final String UN_SUCCESSFUL_TEMPLATE_DESCRIPTION = "UnSuccessful Supplier Templates";
  static final String ORDER_FORM_TEMPLATE_DESCRIPTION = "Order Form Templates";
  static final String AWARDED_FILE_TYPE = "AWARDED";
  static final String UN_SUCCESSFUL_SUPPLIER_FILE_TYPE = "UNSUCCESSFUL_AWARD";
  static final String ORDER_FORM_FILE_TYPE = "ORDER_FORM";

  /**
   * Pre-Award or Edit-Pre-Award or Complete Award to the supplied suppliers.
   *
   * @param principal
   * @param projectId
   * @param eventId
   * @param awardAction
   * @param award
   * @return status
   */
  @Deprecated
  public String createOrUpdateAward(final String principal, final Integer projectId,
      final String eventId, final AwardState awardState, final Award2AllOf award,
      final Integer awardId) {
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var buyerUser = userService.resolveBuyerUserProfile(principal)
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND));

    if (award.getSuppliers().size() > 1) {
      throw new JaggaerRPAException(AWARDS_TO_MUTLIPLE_SUPPLIERS);
    }

    var validSuppliers = rpaGenericService.getValidSuppliers(procurementEvent, award.getSuppliers()
        .stream().map(OrganizationReference1::getId).collect(Collectors.toList()));

    var validSupplierName =
        validSuppliers.getFirst().stream().map(e -> e.getCompanyData().getName()).findFirst()
            .orElseThrow(() -> new JaggaerRPAException(SUPPLIERS_NOT_FOUND));

    var awardAction = AwardState.AWARD.equals(awardState) ? AWARD : PRE_AWARD;
    if (awardId != null) {
      awardAction = AwardState.AWARD.equals(awardState) ? AWARD : EDIT_PRE_AWARD;
    }
    log.info("SupplierName {} and Award-action {}", validSupplierName, awardAction);
    // Creating RPA process input string
    var inputBuilder = RPAProcessInput.builder().userName(buyerUser.getEmail())
        .password(rpaGenericService.getBuyerEncryptedPassword(buyerUser.getUserId()))
        .ittCode(procurementEvent.getExternalReferenceId()).awardAction(awardAction)
        .supplierName(validSupplierName);
    try {
      return rpaGenericService.callRPAMessageAPI(inputBuilder.build(), RPAProcessNameEnum.AWARDING);
    } catch (JaggaerRPAException je) {
      // End Evaluation
      if (je.getMessage().contains(RPA_END_EVALUATION_ERROR_DESC)) {
        this.callEndEvaluation(buyerUser.getEmail(),
            rpaGenericService.getBuyerEncryptedPassword(buyerUser.getUserId()),
            procurementEvent.getExternalReferenceId());
        return rpaGenericService.callRPAMessageAPI(inputBuilder.build(),
            RPAProcessNameEnum.AWARDING);
      }
      throw je;
    }
  }
  
  /**
   * Pre-Award or Award to the supplied suppliers.
   *
   * @param principal
   * @param projectId
   * @param eventId
   * @param awardAction
   * @param award
   * @param awardId - For Future use
   * @return status
   */
  public String createOrUpdateAwardRfx(final String principal, final Integer projectId,
      final String eventId, final AwardState awardState, final Award2AllOf award, final Integer awardId) {
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var buyerUser = userService.resolveBuyerUserProfile(principal)
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND));

    if (award.getSuppliers().size() > 1) {
      throw new JaggaerRPAException(AWARDS_TO_MUTLIPLE_SUPPLIERS);
    }
    var validSuppliers = rpaGenericService.getValidSuppliers(procurementEvent, award.getSuppliers()
        .stream().map(OrganizationReference1::getId).collect(Collectors.toList()));
    
    //TODO delete once score testing is done 
    jaggaerService.completeTechnical(procurementEvent, buyerUser.getUserId());
    return jaggaerService.awardOrPreAwardRfx(procurementEvent, buyerUser.getUserId(),
        validSuppliers.getFirst().stream().findFirst()
            .orElseThrow(() -> new JaggaerRPAException(SUPPLIERS_NOT_FOUND)).getCompanyData()
            .getId().toString(),
        awardState);
  }

  /**
   * Calls RPA to End Evaluation
   */
  @Deprecated
  public String callEndEvaluation(final String userEmail, final String password,
      final String externalReferenceId) {
    log.info("Calling End Evaluation for {}", externalReferenceId);
    // Creating RPA process input string
    var inputBuilder = RPAProcessInput.builder().userName(userEmail).password(password)
        .ittCode(externalReferenceId);
    return rpaGenericService.callRPAMessageAPI(inputBuilder.build(),
        RPAProcessNameEnum.END_EVALUATION);
  }

  /**
   * Lists the template documents for an award.
   *
   * @param Set<DocumentTemplate>
   * @return a collection of document summaries
   */
  public Collection<DocumentSummary> getAwardTemplates(final Integer procId, final String eventId) {
    var documentSummaries = new HashSet<DocumentSummary>();
    validationService.validateProjectAndEventIds(procId, eventId);
    documentSummaries
        .addAll(getTemplates(retryableTendersDBDelegate.findByEventStage(AWARDED_FILE_TYPE),
            AWARDED_TEMPLATE_DESCRIPTION));
    documentSummaries
        .addAll(getTemplates(retryableTendersDBDelegate.findByEventStage(ORDER_FORM_FILE_TYPE),
            ORDER_FORM_TEMPLATE_DESCRIPTION));
    documentSummaries.addAll(
        getTemplates(retryableTendersDBDelegate.findByEventStage(UN_SUCCESSFUL_SUPPLIER_FILE_TYPE),
            UN_SUCCESSFUL_TEMPLATE_DESCRIPTION));
    return documentSummaries;
  }

  /**
   * Gets award specific template documents
   *
   * @param procId
   * @param eventId
   * @param documentKey containing the template ID anf fileType
   * @return a document attachment containing the template files
   */
  @SneakyThrows
  public Collection<DocumentAttachment> getAwardTemplate(final Integer procId, final String eventId,
      final DocumentsKey documentKey) {
    var documentAttachments = new HashSet<DocumentAttachment>();
    validationService.validateProjectAndEventIds(procId, eventId);
    var docs = retryableTendersDBDelegate.findByEventStage(documentKey.getFileType());
    var resources = docs.stream()
        .map(template -> documentTemplateResourceService.getResource(template.getTemplateUrl()))
        .collect(Collectors.toList());
    for (Resource resource : resources) {
      documentAttachments.add(DocumentAttachment.builder().fileName(resource.getFilename())
          .data(IOUtils.toByteArray(resource.getInputStream()))
          .contentType(Constants.MEDIA_TYPE_DOCX).build());
    }
    return documentAttachments;
  }

  @SneakyThrows
  private Set<DocumentSummary> getTemplates(final Set<DocumentTemplate> documentTemplates,
      final String description) {
    var documentSummaries = new HashSet<DocumentSummary>();
    for (DocumentTemplate documentTemplate : documentTemplates) {
      var templateResource =
          documentTemplateResourceService.getResource(documentTemplate.getTemplateUrl());
      var docKey = new DocumentsKey(documentTemplate.getEventStage(), description,
          DocumentAudienceType.BUYER);
      var docSummary = new DocumentSummary().fileName(templateResource.getFilename())
          .id(docKey.getDocumentId()).fileSize(templateResource.contentLength())
          .description(description).audience(docKey.getAudience());
      if (documentSummaries.stream().noneMatch(e -> e.getId().equals(docSummary.getId()))) {
        documentSummaries.add(docSummary);
      }
    }
    return documentSummaries;
  }
}
