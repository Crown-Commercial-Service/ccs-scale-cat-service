package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPublicDetail.StatusEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.search.ProcurementEventSearch;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.repo.search.SearchProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
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
  
  @Value("${config.oppertunities.published.batch.size: 100}")
  private int bathcSize;
  
  @Transactional
  @Scheduled(cron = "${config.external.projects.sync.schedule}")
  @SchedulerLock(name = "ProjectsToOpenSearch_scheduledTask", 
  lockAtLeastForString = "PT5M", lockAtMostForString = "PT10M")
  public void saveProjectsDataToOpenSearch() {
    log.info("Started projects data to open search scheduler process");
    var events =
        retryableTendersDBDelegate.findPublishedEventsByAgreementId(DOS6_AGREEMENT_ID);
    log.info("Dos6 agreements count to update in opensearch: {}", events.size());
    
    var agreementDetails = agreementsService.getAgreementDetails(DOS6_AGREEMENT_ID);
    this.reinstateIndex();
    this.saveProjectDataAsBatches(events, agreementDetails);
    
    log.info("Successfully updated projects data in open search");
  }
  
  private void saveProjectDataAsBatches(Set<ProcurementEvent> events,
      AgreementDetail agreementDetail) {
    var eventSearchDataList = new ArrayList<ProcurementEventSearch>();
    List<List<ProcurementEvent>> batches =
        TendersAPIModelUtils.getBatches(new ArrayList<ProcurementEvent>(events), bathcSize);
    for (List<ProcurementEvent> batch : batches) {
      mapToOpenSearch(batch, eventSearchDataList, agreementDetail);
      searchProjectRepo.saveAll(eventSearchDataList);
      log.info("successfully updated events: "+eventSearchDataList.size());
      eventSearchDataList.clear();
    }
  }
  
  private List<ProcurementEventSearch> mapToOpenSearch(List<ProcurementEvent> events,
      List<ProcurementEventSearch> eventSearchDataList,  AgreementDetail agreementDetails) {

    for (ProcurementEvent event : events) {
      try {
        var lotDetails =
            agreementsService.getLotDetails(DOS6_AGREEMENT_ID, event.getProject().getLotNumber());
        var organisationIdentity =
            conclaveService.getOrganisationIdentity(
                event.getProject().getOrganisationMapping().getOrganisationId());

        var firstAndLastPublishedEvent =
            EventsHelper.getFirstAndLastPublishedEvent(event.getProject());
        event = firstAndLastPublishedEvent.getLeft();
        
        var status = EventStatusHelper.getEventStatus(event);
        var subStatus = populateSubStatus(firstAndLastPublishedEvent, status);

        var eventSearchData = ProcurementEventSearch.builder()
            .projectId(event.getProject().getId()).description(getSummaryOfWork(event))
            .budgetRange(TemplateDataExtractor.getBudgetRangeData(event))
            .status(status).subStatus(subStatus).buyerName(organisationIdentity.get().getIdentifier().getLegalName())
            .projectName(event.getProject().getProjectName()).location(TemplateDataExtractor.getLocation(event))
            .lot(event.getProject().getLotNumber()).lotDescription(lotDetails.getDescription()).lastUpdated(event.getUpdatedAt().toString())
            .agreement(agreementDetails.getName()).build();
        eventSearchDataList.add(eventSearchData);
      } catch (Exception e) {
        log.error("Error while saving project details to opensearch", e);
      }
    }
    return eventSearchDataList;
  }

  private String populateSubStatus(
      Pair<ProcurementEvent, ProcurementEvent> firstAndLastPublishedEvent, String status) {
    if (Objects.nonNull(firstAndLastPublishedEvent.getRight())) {
      return EventStatusHelper.getSubStatus(firstAndLastPublishedEvent.getRight());
    }
    if (StatusEnum.CLOSED.getValue().equals(status)) {
      return EventStatusHelper.getSubStatus(firstAndLastPublishedEvent.getLeft());
    }
    return "";
  }
  
  private static String getSummaryOfWork(ProcurementEvent event) {
    try {
      if (Objects.nonNull(event.getProcurementTemplatePayload())) {
        var summary = EventsHelper.getData("Criterion 3", "Group 3", "Question 1",
            event.getProcurementTemplatePayload().getCriteria());
        if (!StringUtils.isBlank(summary)) {
          return summary;
        }
      }
    } catch (Exception e) {
      // TODO: handle exception
    }
    return null;
  }
  
  private void reinstateIndex() {
    try {
      searchProjectRepo.deleteAll();
      log.info("delete data in opensearch");
    } catch (Exception e) {
    }
  }
}
