package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TeamMember;

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

  @Bean("DocumentValueAdaptorExternalEventID")
  public DocGenValueAdaptor documentValueAdaptorExternalEventID() {
    return (event, requestCache) -> event.getEventID();
  }

  @Bean("DocumentValueAdaptorLotName")
  public DocGenValueAdaptor documentValueAdaptorLotName() {
    return (event, requestCache) -> agreementService
        .getLotDetails(event.getProject().getCaNumber(), event.getProject().getLotNumber())
        .getName();
  }

  @Bean("DocumentValueAdaptorCAName")
  public DocGenValueAdaptor documentValueAdaptorCAName() {
    return (event, requestCache) -> agreementService
        .getAgreementDetails(event.getProject().getCaNumber()).getName();
  }

  @Bean("DocumentValueAdaptorOrgIDType")
  @RequestScope
  public DocGenValueAdaptor documentValueAdaptorOrgIDType() {
    return (event, requestCache) -> getProjectOrgFromConclave(event, requestCache).getIdentifier()
        .getScheme();
  }

  @Bean("DocumentValueAdaptorOrgID")
  @RequestScope
  public DocGenValueAdaptor documentValueAdaptorOrgID() {
    return (event, requestCache) -> getProjectOrgFromConclave(event, requestCache).getIdentifier()
        .getId();
  }

  @Bean("DocumentValueAdaptorProcLead")
  @RequestScope
  public DocGenValueAdaptor documentValueAdaptorProcLead() {
    return (event, requestCache) -> getProcurementProjectLead(event, requestCache).getOCDS()
        .getContact().getName();
  }

  @Bean("DocumentValueAdaptorProcLeadTel")
  @RequestScope
  public DocGenValueAdaptor documentValueAdaptorProcLeadTel() {
    return (event, requestCache) -> getProcurementProjectLead(event, requestCache).getOCDS()
        .getContact().getTelephone();
  }

  @Bean("DocumentValueAdaptorProcLeadEmail")
  @RequestScope
  public DocGenValueAdaptor documentValueAdaptorProcLeadEmail() {
    return (event, requestCache) -> getProcurementProjectLead(event, requestCache).getOCDS()
        .getContact().getEmail();
  }

  @Bean("DocumentValueAdaptorProcLeadOrgWebsite")
  public DocGenValueAdaptor documentValueAdaptorProcLeadOrgWebsite() {
    return (event, requestCache) -> "PROC_LEAD_ORG_WEBSITE_TODO";
  }

  private OrganisationProfileResponseInfo getProjectOrgFromConclave(final ProcurementEvent event,
      final Map<String, Object> requestCache) {

    return (OrganisationProfileResponseInfo) requestCache.computeIfAbsent("CACHE_KEY_PRJ_ORG",
        k -> {
          var projectOrgId = Optional.ofNullable(event.getProject().getOrganisationMapping())
              .orElseThrow(() -> new TendersDBDataException(
                  "Project [" + event.getProject().getId() + "] has no org mapping"))
              .getOrganisationId();
          return conclaveService.getOrganisation(projectOrgId)
              .orElseThrow(() -> new TendersDBDataException(
                  "Project org with ID: [" + projectOrgId + "] not found in Conclave"));
        });
  }

  private TeamMember getProcurementProjectLead(final ProcurementEvent event,
      final Map<String, Object> requestCache) {

    return (TeamMember) requestCache.computeIfAbsent("CACHE_KEY_PROC_LEAD", k -> {

      var projectTeamMembers =
          procurementProjectService.getProjectTeamMembers(event.getProject().getId());
      return projectTeamMembers.stream().filter(tm -> tm.getNonOCDS().getProjectOwner()).findFirst()
          .orElseThrow(() -> new TendersDBDataException(
              "Project [" + event.getProject().getId() + "] has no procurement lead"));
    });
  }

}
