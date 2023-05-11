package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.annotation.RequestScope;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TeamMember;
import uk.gov.crowncommercial.dts.scale.cat.service.documentupload.DocumentUploadService;

/**
 * Declares beans representing document value adaptors (used as value sources in RFI/EOI document
 * generation)
 */
@Configuration
@RequiredArgsConstructor
public class DocGenValueAdaptors {

  private final AgreementsService agreementService;
  private final ConclaveService conclaveService;
  private final ProcurementProjectService procurementProjectService;
  private final DocumentUploadService documentUploadService;

  @Bean("DocumentValueAdaptorExternalEventID")
  public DocGenValueAdaptor documentValueAdaptorExternalEventID() {
    return (event, requestCache) -> List.of(event.getEventID());
  }

  @Bean("DocumentValueAdaptorLotName")
  public DocGenValueAdaptor documentValueAdaptorLotName() {
    return (event,
        requestCache) -> List.of(agreementService
            .getLotDetails(event.getProject().getCaNumber(), event.getProject().getLotNumber())
            .getName());
  }

  @Bean("DocumentValueAdaptorCAName")
  public DocGenValueAdaptor documentValueAdaptorCAName() {
    return (event, requestCache) -> List
        .of(agreementService.getAgreementDetails(event.getProject().getCaNumber()).getName());
  }

  @Bean("DocumentValueAdaptorOrgIDType")
  @Scope("prototype")
  public DocGenValueAdaptor documentValueAdaptorOrgIDType() {
    return (event, requestCache) -> List
        .of(getProjectOrgFromConclave(event, requestCache).getIdentifier().getScheme());
  }

  @Bean("DocumentValueAdaptorOrgID")
  @Scope("prototype")
  public DocGenValueAdaptor documentValueAdaptorOrgID() {
    return (event, requestCache) -> List
        .of(getProjectOrgFromConclave(event, requestCache).getIdentifier().getId());
  }

  @Bean("DocumentValueAdaptorOrgName")
  @Scope("prototype")
  public DocGenValueAdaptor documentValueAdaptorOrgName() {
    return (event, requestCache) -> List
        .of(getBuyerOrgName(event, requestCache).getIdentifier().getLegalName());
  }

  @Bean("DocumentValueAdaptorPublishDate")
  @Scope("prototype")
  public DocGenValueAdaptor documentValueAdaptorPublishDate() {
    var formattedDatetime = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    return (event, requestCache) -> List.of(formattedDatetime);
  }

  @Bean("DocumentValueAdaptorPublishDateAndTime")
  @Scope("prototype")
  public DocGenValueAdaptor documentValueAdaptorPublishDateAndTime() {

    return (event, requestCache) -> List.of(getPublishDate(event, requestCache));
  }

  @Bean("DocumentValueAdaptorProcLead")
  @Scope("prototype")
  public DocGenValueAdaptor documentValueAdaptorProcLead() {
    return (event, requestCache) -> List
        .of(getProcurementProjectLead(event, requestCache).getOCDS().getContact().getName());
  }

  @Bean("DocumentValueAdaptorProcLeadTel")
  @Scope("prototype")
  public DocGenValueAdaptor documentValueAdaptorProcLeadTel() {
    return (event, requestCache) -> List
        .of(getProcurementProjectLead(event, requestCache).getOCDS().getContact().getTelephone());
  }

  @Bean("DocumentValueAdaptorProcLeadEmail")
  @Scope("prototype")
  public DocGenValueAdaptor documentValueAdaptorProcLeadEmail() {
    return (event, requestCache) -> List
        .of(getProcurementProjectLead(event, requestCache).getOCDS().getContact().getEmail());
  }

  @Bean("DocumentValueAdaptorProcLeadOrgWebsite")
  public DocGenValueAdaptor documentValueAdaptorProcLeadOrgWebsite() {
    return (event, requestCache) -> List.of("");
  }

  @Bean("DocumentValueAdaptorCommonGoodsServices")
  public DocGenValueAdaptor documentValueAdaptorCommonGoodsServices() {
    return (event, requestCache) -> List.of("project and consulting services");
  }
  
  @Bean("DocumentValueAdaptorProjectId")
  public DocGenValueAdaptor documentValueAdaptorProjectId() {
    return (event, requestCache) -> List.of(String.valueOf(event.getProject().getId()));
  }
  
  @Bean("DocumentValueAdaptorPricingScheduleFileName")
  public DocGenValueAdaptor documentValueAdaptorPricingScheduleFileName() {
    return null;
  }
  
  @Bean("DocumentValueAdaptorTCFileNames")
  public DocGenValueAdaptor documentValueAdaptorTCFileNames() {
    return null;
  }
  
  @Bean("DocumentValueAdaptorAssessmentFileNames")
  public DocGenValueAdaptor documentValueAdaptorAssessmentFileNames() {
    return null;
  }

  @Bean("DocumentValueAdaptorUploadedleNames")
  public DocGenValueAdaptor documentValueAdaptorUploadedFileNames() {
    return (event, requestCache) -> (getUploadedDocumentNames(event, requestCache));
  }
  
  private OrganisationProfileResponseInfo getProjectOrgFromConclave(final ProcurementEvent event,
      final Map<String, Object> requestCache) {

    return (OrganisationProfileResponseInfo) requestCache.computeIfAbsent("CACHE_KEY_PRJ_ORG",
        k -> {
          var projectOrgId = Optional.ofNullable(event.getProject().getOrganisationMapping())
              .orElseThrow(() -> new TendersDBDataException(
                  "Project [" + event.getProject().getId() + "] has no org mapping"))
              .getOrganisationId();
          return conclaveService.getOrganisationIdentity(projectOrgId)
              .orElseThrow(() -> new TendersDBDataException(
                  "Project org with ID: [" + projectOrgId + "] not found in Conclave"));
        });
  }

  private String getPublishDate(final ProcurementEvent event, final Map<String, Object> requestCache){
    var formattedDatetime = event.getPublishDate() == null ? OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
            : OffsetDateTime.ofInstant(event.getPublishDate(), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
    return formattedDatetime;
  }
  private TeamMember getProcurementProjectLead(final ProcurementEvent event,
      final Map<String, Object> requestCache) {

    return (TeamMember) requestCache.computeIfAbsent("CACHE_KEY_PROC_LEAD", k -> {

      var projectTeamMembers = procurementProjectService
          .getProjectTeamMembers(event.getProject().getId(), "DOC_GEN_ADAPTOR");
      return projectTeamMembers.stream().filter(tm -> tm.getNonOCDS().getProjectOwner()).findFirst()
          .orElseThrow(() -> new TendersDBDataException(
              "Project [" + event.getProject().getId() + "] has no procurement lead"));
    });
  }

  private OrganisationProfileResponseInfo getBuyerOrgName(final ProcurementEvent event,
      final Map<String, Object> requestCache) {
    return (OrganisationProfileResponseInfo) requestCache.computeIfAbsent("CACHE_KEY_ORG_NAME",
        k -> {

          var projectOrgId = Optional.ofNullable(event.getProject().getOrganisationMapping())
              .orElseThrow(() -> new TendersDBDataException(
                  "Project [" + event.getProject().getId() + "] has no org mapping"))
              .getOrganisationId();
          return conclaveService.getOrganisationIdentity(projectOrgId)
              .orElseThrow(() -> new TendersDBDataException(
                  "Project org with ID: [" + projectOrgId + "] not found in Conclave"));
        });

  }
  
  private List<String> getUploadedDocumentNames(final ProcurementEvent event,
      final Map<String, Object> requestCache) {
    var docs = documentUploadService.findDocumentByEvent(event);
    return docs.stream().map(f -> DocumentKey.fromString(f.getDocumentId()).getFileName()).collect(Collectors.toList());
  }


}
