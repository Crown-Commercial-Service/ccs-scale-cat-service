package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPublicDetail.StatusEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
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
  
  @Value("${config.oppertunities.published.batch.size: 80}")
  private int bathcSize;
  
  @Transactional
  @Scheduled(cron = "${config.external.projects.sync.schedule}")
  @SchedulerLock(name = "ProjectsToOpenSearch_scheduledTask", 
  lockAtLeastFor = "PT5M", lockAtMostFor = "PT10M")
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
  
  private void saveProjectDataAsBatches(Set<ProcurementProject> events,
      AgreementDetail agreementDetail) {
    var eventSearchDataList = new ArrayList<ProcurementEventSearch>();
    List<List<ProcurementProject>> batches =
        TendersAPIModelUtils.getBatches(new ArrayList<ProcurementProject>(events), bathcSize);
    for (List<ProcurementProject> batch : batches) {
      mapToOpenSearch(batch, eventSearchDataList, agreementDetail);
      searchProjectRepo.saveAll(eventSearchDataList);
      log.info("successfully updated events: "+eventSearchDataList.size());
      eventSearchDataList.clear();
    }
  }
  
  private List<ProcurementEventSearch> mapToOpenSearch(List<ProcurementProject> events,
      List<ProcurementEventSearch> eventSearchDataList,  AgreementDetail agreementDetails) {

    var eventSearchDataListDTO = new ArrayList<ProcurementEventSearchDTO>();
    
    for (ProcurementProject project : events) {
      try {
        var firstAndLastPublishedEvent = EventsHelper.getFirstAndLastPublishedEvent(project);
        var event = firstAndLastPublishedEvent.getLeft();

        var lotDetails = agreementsService.getLotDetails(DOS6_AGREEMENT_ID, project.getLotNumber());
        var organisationIdentity = conclaveService
            .getOrganisationIdentity(project.getOrganisationMapping().getOrganisationId());
        
        String srfxId = null;
        if (Objects.nonNull(firstAndLastPublishedEvent.getRight())) {
          srfxId = firstAndLastPublishedEvent.getRight().getExternalEventId();
        }

        var eventSearchDataDTO = ProcurementEventSearchDTO.builder().rfxId(firstAndLastPublishedEvent.getLeft().getExternalEventId())
            .secondRfxId(srfxId).projectId(event.getProject().getId()).description(getSummaryOfWork(event))
            .budgetRange(TemplateDataExtractor.getBudgetRangeData(event))
            .buyerName(organisationIdentity.get().getIdentifier().getLegalName())
            .projectName(event.getProject().getProjectName()).location(TemplateDataExtractor.getLocation(event))
            .lot(event.getProject().getLotNumber()).lotDescription(lotDetails.getDescription()).lastUpdated(event.getUpdatedAt().getEpochSecond())
            .agreement(agreementDetails.getName()).build();
        
        eventSearchDataListDTO.add(eventSearchDataDTO);
      } catch (Exception e) {
        log.error("Error while saving project details to opensearch", e);
      }
    }
    populateStatus(eventSearchDataListDTO);
    populateSubStatus(eventSearchDataListDTO);
    populateSearchData(eventSearchDataListDTO, eventSearchDataList);
    return eventSearchDataList;
  }
  
  private void populateStatus(List<ProcurementEventSearchDTO> searchDataDTO) {
    Set<String> rfxIds = searchDataDTO.stream().map(e -> e.getRfxId()).collect(Collectors.toSet());
    var rfxResponse =
        jaggaerService.searchRFxWithComponents(rfxIds, Set.of("supplier_Response_Counters"));
    
    // removed broken projects
    rfxResponse = TemplateDataExtractor.removeBrokenEvents(rfxResponse);
    
    for (ExportRfxResponse exportRfxResponse : rfxResponse) {
      for (ProcurementEventSearchDTO data : searchDataDTO) {
        if (data.getRfxId().equals(exportRfxResponse.getRfxSetting().getRfxId())) {
          var eventStatus = EventStatusHelper.getEventStatus(exportRfxResponse.getRfxSetting());
          data.setStatus(eventStatus);
          if (eventStatus.equals(StatusEnum.CLOSED.getValue())) {
            data.setSubStatus(EventStatusHelper.getSubStatus(exportRfxResponse.getRfxSetting()));
          }
        }
      }
    }
  }
  
  private void populateSubStatus(List<ProcurementEventSearchDTO> searchDataDTO) {
    Set<String> rfxIds = searchDataDTO.stream()
        .map(e -> e.getSecondRfxId()).collect(Collectors.toSet());
    Set<ExportRfxResponse> rfxResponse =
        jaggaerService.searchRFxWithComponents(rfxIds, Set.of("supplier_Response_Counters"));
    
    for (ExportRfxResponse exportRfxResponse : rfxResponse) {
      for (ProcurementEventSearchDTO data : searchDataDTO) {
        if (data.getSecondRfxId() != null
            && data.getSecondRfxId().equals(exportRfxResponse.getRfxSetting().getRfxId())) {
          data.setSubStatus(EventStatusHelper.getSubStatus(exportRfxResponse.getRfxSetting()));
        }
      }
    }
  }

  private void populateSearchData(List<ProcurementEventSearchDTO> searchDataDTO,
      List<ProcurementEventSearch> searchDataList) {
    //removed broken projects
    searchDataDTO = searchDataDTO.stream().filter(e -> e.getStatus() != null).toList();
    
    searchDataDTO.stream().forEach(dto -> {
      ProcurementEventSearch searchData = new ProcurementEventSearch();
      BeanUtils.copyProperties(dto, searchData);
      searchDataList.add(searchData);
    });
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

@Setter
@Getter
@Builder
class ProcurementEventSearchDTO {
  String rfxId;
  String secondRfxId;
  String id;
  Integer projectId;
  String projectName;
  String buyerName;
  String location;
  String budgetRange;
  String agreement;
  String lot;
  String lotDescription;
  String status;
  String subStatus;
  String description;
  Long lastUpdated;

}
