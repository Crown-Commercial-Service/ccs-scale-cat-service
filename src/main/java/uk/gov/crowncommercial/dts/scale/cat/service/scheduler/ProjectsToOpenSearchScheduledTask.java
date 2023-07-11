package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DashboardStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.search.ProcurementEventSearch;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.repo.search.SearchProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventStatusHelper;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventsHelper;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectsToOpenSearchScheduledTask {

  private final SearchProjectRepo searchProjectRepo;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private static final String DOS6_AGREEMENT_ID = "RM1043.8";
  private final AgreementsService agreementsService;
  private final ConclaveService conclaveService;
  private final JaggaerService jaggaerService;
  
  @Scheduled(cron = "${config.external.projects.sync.schedule}")
  @Transactional
  public void saveProjectsDataToOpenSearch() {
    log.info("Started projects data to open search scheduler process");

    Set<ProcurementEvent> events =
        retryableTendersDBDelegate.findPublishedEventsByAgreementId(DOS6_AGREEMENT_ID);

    Set<ProcurementEventSearch> eventSearchDataList = new HashSet<ProcurementEventSearch>();
    AgreementDetail agreementDetails = agreementsService.getAgreementDetails(DOS6_AGREEMENT_ID);
    for (ProcurementEvent event : events) {
      try {
        String lotName = null;
        String orgName = null;
        ProcurementEvent stage2Event = null;
        LotDetail lotDetails =
            agreementsService.getLotDetails(DOS6_AGREEMENT_ID, event.getProject().getLotNumber());
        lotName = lotDetails.getDescription();
        Optional<OrganisationProfileResponseInfo> organisationIdentity =
            conclaveService.getOrganisationIdentity(event.getProject().getOrganisationMapping().getOrganisationId());
        orgName = organisationIdentity.get().getIdentifier().getLegalName();

        if (event.getProject().getProcurementEvents().size() > 1) {
          event = EventsHelper.getFirstPublishedEvent(event.getProject());
          stage2Event = EventsHelper.getLastPublishedEvent(event.getProject());
        }

        ProcurementEventSearch eventSearchData = ProcurementEventSearch.builder().id(event.getId())
            .projectId(event.getProject().getId()).description(getSummaryOfWork(event))
            .budgetRange(ProjectsCSVGenerationScheduledTask.getBudgetRangeData(event))
            .status(EventStatusHelper.getEventStatus(Objects.nonNull(stage2Event) ? stage2Event.getTenderStatus()
                    : event.getTenderStatus())).subStatus(getDashboardStatus(event)).buyerName(orgName)
            .projectName(event.getProject().getProjectName()).location("")
            .lot(event.getProject().getLotNumber()).lotDescription(lotName)
            .agreement(agreementDetails.getName()).build();

        eventSearchDataList.add(eventSearchData);
      } catch (Exception e) {
        log.error("Error while saving project details to opensearch" + e.getMessage());
      }
    }
    searchProjectRepo.saveAll(eventSearchDataList);
    log.info("Successfully updated projects data in open search");
  }

  private String getDashboardStatus(ProcurementEvent event) {
    try {
      var exportRfxResponse = jaggaerService.getRfxByComponent(event.getExternalEventId(),
          new HashSet<>(Arrays.asList("OFFERS")));
      DashboardStatus dashboardStatus =
          TendersAPIModelUtils.getDashboardStatus(exportRfxResponse.getRfxSetting(), event);
      return dashboardStatus.getValue();
    } catch (Exception e) {
    }
    return null;
  }
  
  private static String getSummaryOfWork(ProcurementEvent event) {
    if (Objects.nonNull(event.getProcurementTemplatePayload())) {
      var summary = EventsHelper.getData("Criterion 3", "Group 3", "Question 1",
          event.getProcurementTemplatePayload().getCriteria());
      if (!StringUtils.isBlank(summary)) {
        return summary;
      }
    }
    return null;
  }

}
