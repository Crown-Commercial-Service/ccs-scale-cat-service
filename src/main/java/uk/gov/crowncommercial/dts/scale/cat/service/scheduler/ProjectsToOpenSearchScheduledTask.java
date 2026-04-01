package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.util.*;
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
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationIdentifier;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
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
  private static final List<String> AGREEMENT_IDS = List.of("RM1043.8", "RM1043.9");
  private final AgreementsService agreementsService;
  private final ConclaveService conclaveService;
  private final JaggaerService jaggaerService;
  
  @Value("${config.oppertunities.published.batch.size: 80}")
  private int bathcSize;
  
  @Transactional
  // 1316: TODO uncomment after test
  //@Scheduled(cron = "${config.external.projects.sync.schedule}")
  @Scheduled(fixedDelay = 1000 * 60)
  @SchedulerLock(name = "ProjectsToOpenSearch_scheduledTask", 
  lockAtLeastFor = "PT5M", lockAtMostFor = "PT10M")
  public void saveProjectsDataToOpenSearch() {
    log.info("Started projects data to open search scheduler process");
    // 1316: Process DOS6 and DOS7 events
    AGREEMENT_IDS.forEach(agreementId -> {
      try {
        final Set<ProcurementProject> events = retryableTendersDBDelegate.findPublishedEventsByAgreementId(agreementId);
        log.info("AgreementId: {}, Count to update in opensearch: {}", agreementId, events.size());
        final AgreementDetail agreementDetails = agreementsService.getAgreementDetails(agreementId);
        this.reinstateIndex();
        this.saveProjectDataAsBatches(agreementId, events, agreementDetails);
        log.info("Successfully updated projects data in open search for agreementId: {}, size: {}", agreementId, events.size());
      } catch (Exception e) {
        log.error("Error processing OpenSearch for agreementId: {}", agreementId, e);
      }
    });
  }
  
  private void saveProjectDataAsBatches(String agreementId, Set<ProcurementProject> events,
      AgreementDetail agreementDetail) {
    log.info("saveProjectDataAsBatches for agreementId: {}", agreementId);
    var eventSearchDataList = new ArrayList<ProcurementEventSearch>();
    List<List<ProcurementProject>> batches =
        TendersAPIModelUtils.getBatches(new ArrayList<ProcurementProject>(events), bathcSize);
    for (List<ProcurementProject> batch : batches) {
      mapToOpenSearch(agreementId, batch, eventSearchDataList, agreementDetail);
      searchProjectRepo.saveAll(eventSearchDataList);
      log.info("successfully updated events: {} for agreementId: {}", eventSearchDataList.size(), agreementId);
      eventSearchDataList.clear();
    }
  }
  
  private List<ProcurementEventSearch> mapToOpenSearch(String agreementId, List<ProcurementProject> events,
      List<ProcurementEventSearch> eventSearchDataList,  AgreementDetail agreementDetails) {
    log.info("mapToOpenSearch for agreementId: {}", agreementId);
    var eventSearchDataListDTO = new ArrayList<ProcurementEventSearchDTO>();
    
    for (ProcurementProject project : events) {
      try {
        var firstAndLastPublishedEvent = EventsHelper.getFirstAndLastPublishedEvent(project);
        var event = firstAndLastPublishedEvent.getLeft();

        var lotDetails = agreementsService.getLotDetails(agreementId, project.getLotNumber());
        final Optional<OrganisationProfileResponseInfo> organisationIdentity = conclaveService
            .getOrganisationIdentity(project.getOrganisationMapping().getOrganisationId());
        
        String srfxId = null;
        if (Objects.nonNull(firstAndLastPublishedEvent.getRight())) {
          srfxId = firstAndLastPublishedEvent.getRight().getExternalEventId();
        }

        var eventSearchDataDTO = ProcurementEventSearchDTO.builder().rfxId(firstAndLastPublishedEvent.getLeft().getExternalEventId())
            .secondRfxId(srfxId).projectId(event.getProject().getId()).description(getSummaryOfWork(event))
            .budgetRange(TemplateDataExtractor.getBudgetRangeData(event))
            .buyerName(organisationIdentity.map(OrganisationProfileResponseInfo::getIdentifier).map(OrganisationIdentifier::getLegalName).orElse(null))
            .projectName(event.getProject().getProjectName()).location(TemplateDataExtractor.getLocation(event))
            .lot(event.getProject().getLotNumber()).lotDescription(lotDetails.getDescription()).lastUpdated(event.getUpdatedAt().getEpochSecond())
            .agreement(agreementDetails.getName())
                .agreementId(agreementId).build();
        
        eventSearchDataListDTO.add(eventSearchDataDTO);
      } catch (Exception e) {
        log.error("Error while saving project details to opensearch, agreementId: {}", agreementId, e);
      }
    }
    populateStatus(eventSearchDataListDTO);
    populateSubStatus(eventSearchDataListDTO);
    populateSearchData(eventSearchDataListDTO, eventSearchDataList);
    return eventSearchDataList;
  }
  
  private void populateStatus(List<ProcurementEventSearchDTO> searchDataDTO) {
    log.info("populateStatus()");
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
    log.info("populateSubStatus()");
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
    log.info("populateSearchData()");
    //removed broken projects
    searchDataDTO = searchDataDTO.stream().filter(e -> e.getStatus() != null).toList();
    
    searchDataDTO.stream().forEach(dto -> {
      ProcurementEventSearch searchData = new ProcurementEventSearch();
      BeanUtils.copyProperties(dto, searchData);
      searchDataList.add(searchData);
    });
  }
  
  private static String getSummaryOfWork(ProcurementEvent event) {
    log.info("getSummaryOfWork()");
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
      log.error("getSummaryOfWork Error", e);
    }
    return null;
  }
  
  private void reinstateIndex() {
    log.info("reinstateIndex()");
    try {
      searchProjectRepo.deleteAll();
      log.info("Delete data in opensearch by reinstateIndex");
    } catch (Exception e) {
      log.error("reinstateIndex Error", e);
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
  String agreementId;
  String lot;
  String lotDescription;
  String status;
  String subStatus;
  String description;
  Long lastUpdated;

}
