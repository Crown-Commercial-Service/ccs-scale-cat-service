package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventStatusHelper;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventsHelper;

/**
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectsCSVGenerationScheduledTask {

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final AgreementsService agreementsService;
  private final ConclaveService conclaveService;
  private final JaggaerService jaggaerService;
  private final Environment env;
  private final AmazonS3 tendersS3Client;
  private final AWSS3Service tendersS3Service;
  private static final String DOS6_AGREEMENT_ID = "RM1043.8";
  private static final Integer JAGGAER_SUPPLIER_WINNER_STATUS = 3;
  public static final String CSV_FILE_NAME = "oppertunity_data.csv";
  public static final String CSV_FILE_PREFIX = "/Oppertunity/";
  public static final String PROJECT_UI_LINK_KEY = "config.external.s3.oppertunities.ui.link";

  @Scheduled(cron = "${config.external.s3.oppertunities.schedule}")
  @Transactional
  public void generateCSV() {
    log.info("Started oppertunities CSV generation");
    writeOppertunitiesToCsv();
  }

  public void writeOppertunitiesToCsv() {

    var events =
        retryableTendersDBDelegate.findPublishedEventsByAgreementId(DOS6_AGREEMENT_ID);
    log.info("Dos6 agreements count for CSV generation: {}", events.size());
    
    try {
      var tempFile = Files.createTempFile("temp", ".csv");
      var writer =
          new PrintWriter(Files.newBufferedWriter(tempFile, StandardOpenOption.WRITE));
      var csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);

      csvPrinter.printRecord("ID", "Opportunity", "Link", "Framework", "Category",
          "Organization Name", "Buyer Domain", "Location Of The Work", "Published At", "Open For",
          "Expected Contract Length", "Budget range", "Applications from SMEs",
          "Applications from Large Organisations", "Total Organisations", "Status",
          "Winning supplier", "Size of supplier", "Contract amount", "Contract start date",
          "Clarification questions", "Employment status");

      generateCSVInfo(events, csvPrinter);

      csvPrinter.flush();
      csvPrinter.close();

      transferToS3(tempFile);
      log.info("Successfully generated CSV data");
    } catch (Exception e) {
      log.error("Error While generating Projects CSV ", e);
    }
  }

  private void generateCSVInfo(Set<ProcurementEvent> events, CSVPrinter csvPrinter)
      throws Exception {
    try {
      var agreementDetails = agreementsService.getAgreementDetails(DOS6_AGREEMENT_ID);
      for (ProcurementEvent event : events) {
        
        var totalOrganisationsCountAndWinningSupplier = Pair.of("", "");
        var firstAndLastPublishedEvent =
            EventsHelper.getFirstAndLastPublishedEvent(event.getProject());
        event = firstAndLastPublishedEvent.getLeft();
        
        var lotDetails =
            agreementsService.getLotDetails(DOS6_AGREEMENT_ID, event.getProject().getLotNumber());
        var organisationIdentity =
            conclaveService.getOrganisationIdentity(
                event.getProject().getOrganisationMapping().getOrganisationId());
        
        totalOrganisationsCountAndWinningSupplier = getTotalOrganisationsCountAndWinningSupplier(
            Objects.nonNull(firstAndLastPublishedEvent.getRight())
                ? firstAndLastPublishedEvent.getRight()
                : firstAndLastPublishedEvent.getLeft());
        
        csvPrinter.printRecord(event.getProject().getId(), event.getProject().getProjectName(),
            env.getProperty(PROJECT_UI_LINK_KEY), agreementDetails.getName(), lotDetails.getName(),
            organisationIdentity.get().getIdentifier().getLegalName(), organisationIdentity.get()
            .getIdentifier().getUri(), TemplateDataExtractor.getLocation(event), event.getPublishDate(),TemplateDataExtractor.getOpenForCount(event), 
            TemplateDataExtractor.getExpectedContractLength(event),
            StringUtils.isBlank(TemplateDataExtractor.getBudgetRangeData(event)) ? ""
                : TemplateDataExtractor.getBudgetRangeData(event), "", "", totalOrganisationsCountAndWinningSupplier.getRight(),
                EventStatusHelper.getEventStatus(event), totalOrganisationsCountAndWinningSupplier.getLeft(), "", "",
            TemplateDataExtractor.geContractStartData(event), retryableTendersDBDelegate.findQuestionsCountByEventId(event.getId()),
            TemplateDataExtractor.getEmploymentStatus(event));
      }
    } catch (Exception e) {
      log.error("Error while generating CSV generator ", e);
    }
  }
  
  /**
   * @param ProcurementEvent
   * @return winning supplier and suppliers response count
   */
  private Pair<String, String> getTotalOrganisationsCountAndWinningSupplier(
      final ProcurementEvent event) {
    try {
      String wSupplier = "";
      var rfxWithSuppliers =
          jaggaerService.getRfxWithSuppliersOffersAndResponseCounters(event.getExternalEventId());
      if (event.getTenderStatus().equals(TenderStatus.COMPLETE.getValue())) {
        var winningSupplier = rfxWithSuppliers.getSuppliersList().getSupplier()
            .stream().filter(e -> e.getStatusCode() == JAGGAER_SUPPLIER_WINNER_STATUS).findFirst();
        if (winningSupplier.isPresent()) {
          wSupplier = winningSupplier.get().getCompanyData().getName();
        }
      }
      return Pair.of(wSupplier,
          rfxWithSuppliers.getSupplierResponseCounters().getLastRound().getNumSupplResponded()
              + "");

    } catch (Exception e) {
      log.warn("Error while getTotalOrganisationsCountAndWinningSupplier " + e.getMessage());
    }
    return Pair.of("", "");
  }

  /**
   * Send oppertunities CSV file to s3
   */
  private void transferToS3(Path tempFile) {
    try {
      var fileStream = Files.newInputStream(tempFile);
      var tendersS3ObjectKey = CSV_FILE_PREFIX + CSV_FILE_NAME;
      var objectMetadata = new ObjectMetadata();
      objectMetadata.setContentLength(Files.readAllBytes(tempFile).length);
      objectMetadata.setContentType("text/csv");
      tendersS3Client.putObject(tendersS3Service.getCredentials().getBucketName(), tendersS3ObjectKey,
          fileStream, objectMetadata);
      log.info("Successfully uploaded oppertunies file to S3: {}", tendersS3ObjectKey);
      // Delete the temporary file
      Files.delete(tempFile);
    } catch (Exception e) {
      log.error("Error in transfer oppertunies to S3 ", e);
    }
  }
  
}
