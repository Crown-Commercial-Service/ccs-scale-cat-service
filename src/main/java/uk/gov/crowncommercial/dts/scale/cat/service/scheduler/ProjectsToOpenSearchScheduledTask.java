package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
    this.mapToOpenSearch(events, eventSearchDataList, agreementDetails);
    
    searchProjectRepo.saveAll(eventSearchDataList);
    log.info("Successfully updated projects data in open search");
  }
  
  private Set<ProcurementEventSearch> mapToOpenSearch(Set<ProcurementEvent> events,
      Set<ProcurementEventSearch> eventSearchDataList,  AgreementDetail agreementDetails) {

    for (ProcurementEvent event : events) {
      try {
        LotDetail lotDetails =
            agreementsService.getLotDetails(DOS6_AGREEMENT_ID, event.getProject().getLotNumber());
        Optional<OrganisationProfileResponseInfo> organisationIdentity =
            conclaveService.getOrganisationIdentity(
                event.getProject().getOrganisationMapping().getOrganisationId());

        Pair<ProcurementEvent, ProcurementEvent> firstAndLastPublishedEvent =
            EventsHelper.getFirstAndLastPublishedEvent(event.getProject());
        event = firstAndLastPublishedEvent.getLeft();

        ProcurementEventSearch eventSearchData = ProcurementEventSearch.builder().id(event.getId())
            .projectId(event.getProject().getId()).description(getSummaryOfWork(event))
            .budgetRange(TemplateDataExtractor.getBudgetRangeData(event))
            .status(EventStatusHelper.getEventStatus(Objects.nonNull(firstAndLastPublishedEvent.getRight()) ? firstAndLastPublishedEvent.getRight().getTenderStatus()
                    : event.getTenderStatus())).subStatus(getDashboardStatus(event)).buyerName(organisationIdentity.get().getIdentifier().getLegalName())
            .projectName(event.getProject().getProjectName()).location("").lot(event.getProject().getLotNumber())
            .lotDescription(lotDetails.getDescription()).agreement(agreementDetails.getName()).build();

        eventSearchDataList.add(eventSearchData);
      } catch (Exception e) {
        log.error("Error while saving project details to opensearch" + e.getMessage());
      }
    }
    return eventSearchDataList;
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
