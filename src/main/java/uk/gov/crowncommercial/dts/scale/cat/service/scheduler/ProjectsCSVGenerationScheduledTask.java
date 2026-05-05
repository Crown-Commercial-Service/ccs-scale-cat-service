package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Service;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPublicDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventStatusHelper;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventSubStatus;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventsHelper;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectsCSVGenerationScheduledTask {

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final AgreementsService agreementsService;
  private final ConclaveService conclaveService;
  private final JaggaerService jaggaerService;
  private final Environment env;
  private final S3Client tendersS3Client;
  private final AWSS3Service tendersS3Service;
  private static final Integer JAGGAER_SUPPLIER_WINNER_STATUS = 3;
  public static final String CSV_FILE_NAME = "opportunity_data.csv";
  public static final String XLSX_FILE_NAME = "opportunity_data.xlsx";
  public static final String ODS_FILE_NAME = "opportunity_data.ods";
  public static final String CSV_FILE_PREFIX = "/Oppertunity/";
  public static final String PROJECT_UI_LINK_KEY = "config.external.s3.oppertunities.ui.link";
  private static final List<String> AGREEMENT_IDS = List.of("RM1043.9", "RM1043.8");

  @Value("${config.oppertunities.published.batch.size: 20}")
  private int publishedBatchSize;

  @Value("${config.oppertunities.awarded.batch.size: 5}")
  private int awardedBatchSize;

  @Value("${config.oppertunities.published.batch.size: 80}")
  private int batchSize;

  @Scheduled(cron = "${config.external.s3.oppertunities.schedule}")
  @SchedulerLock(name = "CSVGeneration_scheduledTask",
    lockAtLeastFor = "PT5M", lockAtMostFor = "PT10M")
  public void generateCSV() {
    log.info("Started oppertunities CSV generation Time {}",  LocalDateTime.now());
    writeOppertunitiesToCsv();
  }

  public void writeOppertunitiesToCsv() {
    log.info("writeOppertunitiesToCsv()");
    try {
        var tempFile = Files.createTempFile("temp", ".csv");
        var writer = new PrintWriter(Files.newBufferedWriter(tempFile, StandardOpenOption.WRITE));
        writer.write('\ufeff');
        final CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
        csvPrinter.printRecord("ID", "Opportunity", "Link", "Framework", "Category", "Specialist",
              "Organization Name", "Buyer Domain", "Location Of The Work", "Published At", "Open For",
              "Expected Contract Length", "Budget range", "Applications from SMEs",
              "Applications from Large Organisations", "Total Organisations", "Status",
              "Winning supplier", "Size of supplier", "Contract amount", "Contract start date",
              "Clarification questions", "Employment status");
        List<CSVData> csvDataList = new ArrayList<>();
        // NCAS-1314: Download Opportunity Search Results
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
              if (events != null && !events.isEmpty()) {
                log.info("S3 AgreementId {} Count fetched from opensearch {} bathcSize {} Index {}", agreementId, events.size(), batchSize, index);
                populateCSVData(agreementDetails, events, csvDataList);
              }
            } catch (Exception e) {
              log.error("S3 Error processing OpenSearch for agreementId {}", agreementId, e);
            } finally {
              totalEvents += events == null ? 0 : events.size();
            }
          } while (!events.isEmpty());
          log.info("S3 Successfully fetch projects data from OpenSearch for agreementId {} size {}", agreementId, totalEvents);
        });

      populateJaggaerFields(csvDataList);
      populateCSVPrinter(csvDataList, csvPrinter);

      csvPrinter.flush();
      csvPrinter.close();
      log.info("Successfully generated CSV data now initiating transfer to S3 Storage");
      transferToS3(tempFile);
      log.info("CSV Data uploaded to S3 Storage");
    } catch (Exception e) {
      log.error("Error While generating Projects CSV", e);
    }
  }

  private void populateCSVData(AgreementDetail agreementDetails, List<ProcurementProject> events,
                               List<CSVData> csvDataList) {
    log.info("populateCSVData()");
    try {
      for (ProcurementProject project : events) {
        var totalOrganisationsCountAndWinningSupplier = Pair.of("", "");
        var firstAndLastPublishedEvent =
            EventsHelper.getFirstAndLastPublishedEvent(project);
        var event = firstAndLastPublishedEvent.getLeft();

        var lotDetails =
            agreementsService.getLotDetails(agreementDetails.getNumber(), project.getLotNumber());
        var organisationIdentity = conclaveService.getOrganisationIdentity(
            project.getOrganisationMapping().getOrganisationId());

        String rfxId = firstAndLastPublishedEvent.getLeft().getExternalEventId();
        String tStatus = firstAndLastPublishedEvent.getLeft().getTenderStatus();

        String latestRfxId, latestStatus;

        if(Objects.nonNull(firstAndLastPublishedEvent.getRight())){
          latestRfxId = firstAndLastPublishedEvent.getRight().getExternalEventId();
          latestStatus = firstAndLastPublishedEvent.getRight().getTenderStatus();
        }else{
          latestRfxId = rfxId;
          latestStatus = tStatus;
        }

        var csvData = CSVData.builder().firstRfxId(rfxId).tenderstatus(latestStatus)
                .latestRfxId(latestRfxId)
            .projectId(event.getProject().getId()).oppertunity(event.getProject().getProjectName())
            .link(env.getProperty(PROJECT_UI_LINK_KEY) + "/" + event.getProject().getId())
            .framework(agreementDetails.getName()).category(lotDetails.getName())
            .orgName(organisationIdentity.map(obj -> obj.getIdentifier().getLegalName()).orElse(""))
            .buyerDomain(organisationIdentity.map(obj -> obj.getIdentifier().getUri()).orElse(""))
            .locationOfWork(TemplateDataExtractor.getLocation(event))
            .publishedDate(event.getPublishDate())
            .expectedContractLength(TemplateDataExtractor.getExpectedContractLength(event))
            .budgetRange(StringUtils.isBlank(TemplateDataExtractor.getBudgetRangeData(event)) ? ""
                : TemplateDataExtractor.getBudgetRangeData(event))
            .totalOrganisations(totalOrganisationsCountAndWinningSupplier.getRight())
            .winningSupplier(totalOrganisationsCountAndWinningSupplier.getLeft())
            .contractStartDate(TemplateDataExtractor.geContractStartData(event))
            .clarificationQuestions(
                retryableTendersDBDelegate.findQuestionsCountByEventId(event.getId()))
            .employmentStatus(TemplateDataExtractor.getEmploymentStatus(event)).build();
        csvDataList.add(csvData);
      }
    } catch (Exception e) {
      log.error("Error while generating CSV generator ", e);
    }
  }

  private void populateJaggaerFields(List<CSVData> csvDataList) {
    log.info("populateJaggaerFields()");
    Pair<List<CSVData>, List<CSVData>> splitAwardedProjects = splitAwardedProjects(csvDataList);
    List<List<CSVData>> supplierFetchList = TendersAPIModelUtils.getBatches(splitAwardedProjects.getLeft(), awardedBatchSize);
    List<List<CSVData>> published = TendersAPIModelUtils.getBatches(splitAwardedProjects.getRight(), publishedBatchSize);
    for (List<CSVData> dataList : supplierFetchList) {
      Set<String> collect = dataList.stream().map(e -> e.getFirstRfxId()).collect(Collectors.toSet());
      getJaggaerData(dataList, collect, Set.of("SUPPLIERS", "supplier_Response_Counters"));
    }

    for (List<CSVData> dataList : published) {
      Set<String> collect = dataList.stream().map(e -> e.getFirstRfxId()).collect(Collectors.toSet());
      getJaggaerData(dataList, collect, Set.of("supplier_Response_Counters"));
    }
  }

  private void populateCSVPrinter(List<CSVData> csvDataList, CSVPrinter csvPrinter) {
    log.info("populateCSVPrinter()");
    //removed broken projects
    csvDataList = csvDataList.stream().filter(e -> e.getStatus() != null).toList();
    for (CSVData csvData : csvDataList) {
      try {
        csvPrinter.printRecord(csvData.getProjectId(), csvData.getOppertunity(), csvData.getLink(),
            csvData.getFramework(), csvData.getCategory(), "", csvData.getOrgName(),
            csvData.getBuyerDomain(), csvData.getLocationOfWork(), csvData.getPublishedDate(),
            csvData.getOpenFor(), csvData.getExpectedContractLength(), csvData.getBudgetRange(), "",
            "", csvData.getTotalOrganisations(), csvData.getStatus(), csvData.getWinningSupplier(),
            "", "", csvData.getContractStartDate(), csvData.getClarificationQuestions(),
            csvData.getEmploymentStatus());
      } catch (Exception e) {
        log.error("Error populateCSVPrinter", e);
      }
    }
  }

  private void getJaggaerData(List<CSVData> csvDataList,
      Set<String> collect, Set<String> components) {
    try {
      var firstRfxWithComponents =
          jaggaerService.searchRFxWithComponents(collect, components);

      Set<String> latestRfxIds = csvDataList.stream().map(e -> e.getLatestRfxId()).collect(Collectors.toSet());

      var latestRFxWithComponents =
              jaggaerService.searchRFxWithComponents(latestRfxIds, components);

    //removed broken projects
      firstRfxWithComponents = TemplateDataExtractor.removeBrokenEvents(firstRfxWithComponents);

      for (ExportRfxResponse rfx : firstRfxWithComponents) {

        for (CSVData csvData : csvDataList) {
          if (csvData.getFirstRfxId().equals(rfx.getRfxSetting().getRfxId())) {


            populateInitialEntries(rfx, csvData);

            if(csvData.singleRfx()) {
              populateLatestEntries(csvData, csvData.getFirstRfxId(), firstRfxWithComponents);
            }else{
              populateLatestEntries(csvData, csvData.getLatestRfxId(), latestRFxWithComponents);
            }

          }
        }
      }
    } catch (Exception e) {
      log.warn("Error while getTotalOrganisationsCountAndWinningSupplier ", e);
    }
  }

  private static void populateInitialEntries(ExportRfxResponse rfx, CSVData csvData) {
    // Total org count
    csvData.setTotalOrganisations(
        rfx.getSupplierResponseCounters().getLastRound().getNumSupplResponded() + "");
    // Open for
    csvData.setOpenFor(TemplateDataExtractor.getOpenForCount(
        rfx.getRfxSetting().getPublishDate(), rfx.getRfxSetting().getCloseDate()));
    // Status
    csvData.setStatus(EventStatusHelper.getEventStatus(rfx.getRfxSetting()));
  }

  private void populateLatestEntries(CSVData csvData, String rfxId, Set<ExportRfxResponse> latestRFxWithComponents) {

    for(ExportRfxResponse rfx: latestRFxWithComponents){
      if(rfx.getRfxSetting().getRfxId().equals(rfxId)){

        populateAwardedSupplier(csvData, rfx);

        if(csvData.getStatus().equals(ProjectPublicDetail.StatusEnum.CLOSED.getValue())) {
          csvData.setSubStatus(EventStatusHelper.getSubStatus(rfx.getRfxSetting()));
          csvData.setStatus(transformSubStatus(csvData.getStatus(), csvData.getSubStatus()));
        }
      }
    }
  }

  private static void populateAwardedSupplier(CSVData csvData, ExportRfxResponse rfx) {
    String wSupplier = "";
    if (rfx.getSuppliersList() != null) {
      Optional<Supplier> winningSupplier = rfx.getSuppliersList().getSupplier().stream()
              .filter(e -> e.getStatusCode() == JAGGAER_SUPPLIER_WINNER_STATUS).findFirst();
      if (winningSupplier.isPresent()) {
        wSupplier = winningSupplier.get().getCompanyData().getName();
      }
    }
    //winning supper details
    csvData.setWinningSupplier(wSupplier);
  }

  private String transformSubStatus(String status, String subStatus) {
    if(null == subStatus)
      return status;
    EventSubStatus eventSubStatus = EventSubStatus.fromValue(subStatus);
    if(null == eventSubStatus)
      return status;

    switch (eventSubStatus){
      case  AWARDED:
        return "awarded";
      case CANCELLED:
        return "cancelled";
    }
    return status;
  }


  private Pair<List<CSVData>, List<CSVData>> splitAwardedProjects(List<CSVData> collection) {
    List<CSVData> supplierFetchList = new ArrayList<>();
    List<CSVData> firstStageList = new ArrayList<>();

    for(CSVData d : collection){
      if(d.singleRfx()){
        firstStageList.add(d);
      }else{
        supplierFetchList.add(d);
      }
    }

    return Pair.of(supplierFetchList, firstStageList);
  }

  /**
   * Send oppertunities CSV file to s3
   */
  private void transferToS3(Path tempFile) {
    try {
      var fileStream = Files.newInputStream(tempFile);
      var tendersS3ObjectKey = CSV_FILE_PREFIX + CSV_FILE_NAME;
      var fileBytes = Files.readAllBytes(tempFile);
      
      var putObjectRequest = PutObjectRequest.builder()
          .bucket(tendersS3Service.getCredentials().getBucketName())
          .key(tendersS3ObjectKey)
          .contentLength((long) fileBytes.length)
          .contentType("text/csv")
          .build();
      
      tendersS3Client.putObject(putObjectRequest, RequestBody.fromInputStream(fileStream, fileBytes.length));
      log.info("Successfully uploaded oppertunities file to S3: {}", tendersS3ObjectKey);
      // Delete the temporary file
      Files.delete(tempFile);
    } catch (Exception e) {
      log.error("Error in transfer oppertunies to S3 ", e);
    }
  }

}


@Setter
@Getter
@Builder
class CSVData {

  private String firstRfxId;

  private String latestRfxId;

  private String tenderstatus;

  private String subStatus;

  private long projectId, openFor;

  private long clarificationQuestions;

  private Instant publishedDate;

  private String oppertunity, link, framework, category, orgName, buyerDomain, locationOfWork,
      expectedContractLength, budgetRange, totalOrganisations, status, winningSupplier,
      contractStartDate, employmentStatus;

  public boolean singleRfx(){
    return null == latestRfxId || latestRfxId.equalsIgnoreCase(firstRfxId);
  }

}
