package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
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
import uk.gov.crowncommercial.dts.scale.cat.service.MiService;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventStatusHelper;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventsHelper;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectsToOpenSearchScheduledTask {

  private final SearchProjectRepo searchProjectRepo;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private static final List<String> AGREEMENT_IDS = List.of("RM1043.9", "RM1043.8");
  private final AgreementsService agreementsService;
  private final ConclaveService conclaveService;
  private final JaggaerService jaggaerService;
  private final MiService miService;
  
  @Value("${config.oppertunities.published.batch.size: 80}")
  private int batchSize;

  @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS)
  //@Scheduled(cron = "${config.external.projects.sync.schedule}")
  @SchedulerLock(name = "ProjectsToOpenSearch_scheduledTask",
  lockAtLeastFor = "PT5M", lockAtMostFor = "PT10M")
  public void saveProjectsDataToOpenSearch() {
    log.info("Started projects data to open search scheduler process, Time: {}", LocalDateTime.now());
    // 1316: Process DOS6 and DOS7 events
    reinstateIndex();
    AGREEMENT_IDS.forEach(agreementId -> {
        final AgreementDetail agreementDetails = agreementsService.getAgreementDetails(agreementId);
        List<ProcurementProject> events = new ArrayList<>();
        int index = 0;
        int totalEvents = 0;
        do {
          try {
            events.clear();
            events = retryableTendersDBDelegate.findPublishedEventsByAgreementId(agreementId,
                    PageRequest.of(index++, batchSize, Sort.by("project_id").ascending()));
            log.info("AgreementId: {} Count to update in opensearch {} bathcSize {} Index {}", agreementId, events.size(), batchSize, index);
            saveProjectDataAsBatches(agreementId, events, agreementDetails);
            totalEvents += events.size();
          } catch (Exception e) {
            log.error("Error processing OpenSearch for agreementId {}", agreementId, e);
          }
        } while (!events.isEmpty());
        log.info("Successfully updated projects data in open search for agreementId {} size {}", agreementId, totalEvents);
    });
    log.info("saveProjectsDataToOpenSearch successful, Time: {}", LocalDateTime.now());
  }
  
  private void saveProjectDataAsBatches(String agreementId, List<ProcurementProject> events,
      AgreementDetail agreementDetail) {
    log.info("saveProjectDataAsBatches for agreementId {}", agreementId);
    var eventSearchDataList = new ArrayList<ProcurementEventSearch>();
    List<List<ProcurementProject>> batches =
        TendersAPIModelUtils.getBatches(new ArrayList<>(events), batchSize);
    for (List<ProcurementProject> batch : batches) {
      mapToOpenSearch(agreementId, batch, eventSearchDataList, agreementDetail);
      retryableTendersDBDelegate.searchProjectSaveAll(eventSearchDataList);
      log.info("successfully updated events {} for agreementId {}", eventSearchDataList.size(), agreementId);
      eventSearchDataList.clear();
    }
  }
  
  private List<ProcurementEventSearch> mapToOpenSearch(String agreementId, List<ProcurementProject> events,
      List<ProcurementEventSearch> eventSearchDataList,  AgreementDetail agreementDetails) {
    log.info("mapToOpenSearch for agreementId {}", agreementId);
    var eventSearchDataListDTO = new ArrayList<ProcurementEventSearchDTO>();
    
    for (ProcurementProject project : events) {
      try {
        var firstAndLastPublishedEvent = EventsHelper.getFirstAndLastPublishedEvent(project);
        var event = firstAndLastPublishedEvent.getLeft();

        final LotDetail lotDetails = agreementsService.getLotDetails(agreementId, project.getLotNumber());
        final Optional<OrganisationProfileResponseInfo> organisationIdentity = conclaveService
            .getOrganisationIdentity(project.getOrganisationMapping().getOrganisationId());
        
        String srfxId = null;
        if (Objects.nonNull(firstAndLastPublishedEvent.getRight())) {
          srfxId = firstAndLastPublishedEvent.getRight().getExternalEventId();
        }

        var eventSearchDataDTO = ProcurementEventSearchDTO.builder().rfxId(firstAndLastPublishedEvent.getLeft().getExternalEventId())
            .secondRfxId(srfxId).projectId(event.getProject().getId())
            .description(getSummaryOfWork(event))
            .budgetRange("RM1043.8".equalsIgnoreCase(agreementId) ? TemplateDataExtractor.getBudgetRangeData(event) : TemplateDataExtractor.getDos7BudgetRangeData(event))
            .buyerName(organisationIdentity.map(OrganisationProfileResponseInfo::getIdentifier).map(OrganisationIdentifier::getLegalName).orElse(null))
            .projectName(event.getProject().getProjectName())
            .location("RM1043.8".equalsIgnoreCase(agreementId) ? TemplateDataExtractor.getLocation(event) : TemplateDataExtractor.getDos7Location(event))
            .lot(event.getProject().getLotNumber())
            .lotDescription(Optional.ofNullable(lotDetails).map(LotDetail::getDescription).orElse(null))
            .lastUpdated(event.getUpdatedAt().getEpochSecond())
            .lotName(Optional.ofNullable(lotDetails).map(LotDetail::getName).orElse(null))
            .agreement(agreementDetails.getName())
            .agreementId(agreementId)
            .eventId(event.getEventID())
            .eventName(event.getEventName())
            .eventType(event.getEventType())
            .build();
        
        eventSearchDataListDTO.add(eventSearchDataDTO);
      } catch (Exception e) {
        log.error("Error while saving project details to opensearch, agreementId: {}", agreementId, e);
      }
    }
    populateStatus(eventSearchDataListDTO);
    populateSubStatus(eventSearchDataListDTO);
    // Populate MI status for DOS7 project.
    if ("RM1043.9".equalsIgnoreCase(agreementId)) {
      populateMIStatus(eventSearchDataListDTO);
    }
    populateSearchData(eventSearchDataListDTO, eventSearchDataList);
    return eventSearchDataList;
  }
  
  private void populateStatus(List<ProcurementEventSearchDTO> searchDataDTO) {
    log.info("populateStatus()");
    Set<String> rfxIds = searchDataDTO.stream().map(ProcurementEventSearchDTO::getRfxId)
            .filter(Objects::nonNull)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toSet());
    var rfxResponse =
        jaggaerService.searchRFxWithComponents(rfxIds, Set.of("supplier_Response_Counters"));
    
    // removed broken projects
    rfxResponse = TemplateDataExtractor.removeBrokenEvents(rfxResponse);
    
    for (ExportRfxResponse exportRfxResponse : rfxResponse) {
      for (ProcurementEventSearchDTO data : searchDataDTO) {
        if (data.getRfxId() != null && data.getRfxId().equals(exportRfxResponse.getRfxSetting().getRfxId())) {
          var eventStatus = EventStatusHelper.getEventStatus(exportRfxResponse.getRfxSetting());
          data.setStatus(eventStatus);
          if (eventStatus != null && eventStatus.equals(StatusEnum.CLOSED.getValue())) {
            data.setSubStatus(EventStatusHelper.getSubStatus(exportRfxResponse.getRfxSetting()));
          }
        }
      }
    }
  }
  
  private void populateSubStatus(List<ProcurementEventSearchDTO> searchDataDTO) {
    log.info("populateSubStatus()");
    Set<String> rfxIds = searchDataDTO.stream()
        .map(ProcurementEventSearchDTO::getSecondRfxId)
            .filter(Objects::nonNull)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toSet());
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

  private void populateMIStatus(final List<ProcurementEventSearchDTO> searchDataDTO) {
    log.info("populateMIStatus()");
    // 1511: Set MI project status to open.
    searchDataDTO.forEach(
        obj -> {
          if (!miService.findAllByProjectId(String.valueOf(obj.getProjectId())).isEmpty()) {
            log.debug("Setup MI project status to open ProjectId {}", obj.getProjectId());
            obj.setStatus(StatusEnum.OPEN.getValue());
            obj.setSubStatus(null);
          }
        });
  }

  private void populateSearchData(List<ProcurementEventSearchDTO> searchDataDTO,
      List<ProcurementEventSearch> searchDataList) {
    log.info("populateSearchData()");
    //removed broken projects
    searchDataDTO = searchDataDTO.stream().filter(e -> e.getStatus() != null).toList();
    
    searchDataDTO.forEach(dto -> {
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
  String lotName;
  String lotDescription;
  String status;
  String subStatus;
  String description;
  Long lastUpdated;
  String eventId;
  String eventName;
  String eventType;
}
