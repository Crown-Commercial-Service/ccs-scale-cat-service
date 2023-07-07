package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.OppertunitiesS3Config;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;
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
  private final AmazonS3 oppertunitiesS3Client;
  private final OppertunitiesS3Config oppertunitiesS3Config;
  private static final String DOS6_AGREEMENT_ID = "RM1043.8";
  private static final String AWARD_STATUS = "complete";
  private static final Integer JAGGAER_SUPPLIER_WINNER_STATUS = 3;
  private static final String PERIOD_FMT = "%d years, %d months, %d days";
  public static final String CSV_FILE_NAME = "oppertunity_data.csv";

  @Scheduled(cron = "${config.external.s3.oppertunities.schedule}")
  @Transactional
  public void generateCSV() {
    log.info("Started oppertunities CSV generation");
    writeOppertunitiesToCsv();
  }

  public void writeOppertunitiesToCsv() {

    Set<ProcurementEvent> events =
        retryableTendersDBDelegate.findPublishedEventsByAgreementId(DOS6_AGREEMENT_ID);
    log.info("Dos6 agreements count: {}", events.size());
    try {
      Path tempFile = Files.createTempFile("temp", ".csv");
      PrintWriter writer =
          new PrintWriter(Files.newBufferedWriter(tempFile, StandardOpenOption.WRITE));
      CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);

      csvPrinter.printRecord("ID", "Opportunity", "Link", "Framework", "Category",
          "Organization Name", "Buyer Domain", "Published At", "Open For",
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

    AgreementDetail agreementDetails = agreementsService.getAgreementDetails(DOS6_AGREEMENT_ID);
    for (ProcurementEvent event : events) {
      String lotName = null;
      String orgName = null;
      String domainName = null;
      try {
        LotDetail lotDetails =
            agreementsService.getLotDetails(DOS6_AGREEMENT_ID, event.getProject().getLotNumber());
        lotName = lotDetails.getName();
        Optional<OrganisationProfileResponseInfo> organisationIdentity =
            conclaveService.getOrganisationIdentity(
                event.getProject().getOrganisationMapping().getOrganisationId());
        orgName = organisationIdentity.get().getIdentifier().getLegalName();
        domainName = organisationIdentity.get().getIdentifier().getUri();
      } catch (Exception e) {
        log.error("Error while getting lot and org details in CSV generator" + e.getMessage());
      }
      Pair<String, String> totalOrganisationsCountAndWinningSupplier =
          getTotalOrganisationsCountAndWinningSupplier(event);
      
      csvPrinter.printRecord(event.getProject().getId(), event.getProject().getProjectName(),
          env.getProperty("link"), agreementDetails.getName(), lotName, orgName, domainName,
          event.getPublishDate(), getOpenForCount(event), getExpectedContractLength(event),
          StringUtils.isBlank(EventsHelper.getBudgetRangeData(event)) ? ""
              : EventsHelper.getBudgetRangeData(event), "", "", totalOrganisationsCountAndWinningSupplier.getSecond(),
          EventsHelper.getEventStatus(event.getTenderStatus()), totalOrganisationsCountAndWinningSupplier.getFirst(), "", "", geContractStartData(event),
          retryableTendersDBDelegate.findQuestionsCountByEventId(event.getId()), getEmploymentStatus(event));
    }
  }

  private Long getOpenForCount(ProcurementEvent event) {
    if (Objects.nonNull(event.getPublishDate()) && Objects.nonNull(event.getCloseDate())) {
      return ChronoUnit.DAYS.between(event.getPublishDate(), event.getCloseDate());
    }
    return null;
  }
  
  private Pair<String, String> getTotalOrganisationsCountAndWinningSupplier(
      final ProcurementEvent event) {
    try {
      String wSupplier = null;
      ExportRfxResponse rfxWithSuppliers =
          jaggaerService.getRfxWithSuppliers(event.getExternalEventId());
      if (event.getTenderStatus().equals(AWARD_STATUS)) {
        Optional<Supplier> winningSupplier = rfxWithSuppliers.getSuppliersList().getSupplier()
            .stream().filter(e -> e.getStatusCode() == JAGGAER_SUPPLIER_WINNER_STATUS).findFirst();
        if (winningSupplier.isPresent()) {
          wSupplier = winningSupplier.get().getCompanyData().getName();
        }
      }
      return Pair.of(wSupplier,
          rfxWithSuppliers.getSupplierResponseCounters().getLastRound().getNumSupplResponded()+"");

    } catch (Exception e) {
    }
    return Pair.of("", "");
  }

  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  private String getExpectedContractLength(final ProcurementEvent event) {
    // lot 1 & lot 3-> Contract Length
    if (Objects.nonNull(event.getProcurementTemplatePayload())) {
      try {
        String dataFromJSONDataTemplate = EventsHelper.getData("Criterion 3", "Group 18",
            "Question 12", event.getProcurementTemplatePayload().getCriteria());
        if (Objects.nonNull(dataFromJSONDataTemplate)) {
          var period = Period.parse(dataFromJSONDataTemplate);
          return String.format(PERIOD_FMT, period.getYears(), period.getMonths(), period.getDays());
        }
      } catch (DateTimeParseException e) {
      }
    }
    return "";
  }

  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  private String geContractStartData(final ProcurementEvent event) {
    if (Objects.nonNull(event.getProcurementTemplatePayload())) {
      if (event.getProject().getLotNumber() == "1") {
        return EventsHelper.getData("Criterion 1", "Key Dates", "Question 13",
            event.getProcurementTemplatePayload().getCriteria());
      } else if (event.getProject().getLotNumber() == "3") {
        return EventsHelper.getData("Criterion 1", "Key Dates", "Question 11",
            event.getProcurementTemplatePayload().getCriteria());
      }
    }
    return "";
  }
  
  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  private String getEmploymentStatus(final ProcurementEvent event) {
    if (Objects.nonNull(event.getProcurementTemplatePayload())) {
      if (event.getProject().getLotNumber() == "1") {
        return EventsHelper.getData("Criterion 1", "Group 21", "Question 1",
            event.getProcurementTemplatePayload().getCriteria());
      } else if (event.getProject().getLotNumber() == "3") {
        return EventsHelper.getData("Criterion 1", "Key Dates", "Question 11",
            event.getProcurementTemplatePayload().getCriteria());
      }
    }
    return "";
  }

  private void transferToS3(Path tempFile) throws IOException {
    try {
      InputStream fileStream = Files.newInputStream(tempFile);
      var objectPrefix = org.springframework.util.StringUtils.hasText(oppertunitiesS3Config.getObjectPrefix())
          ? oppertunitiesS3Config.getObjectPrefix() + "/"
          : "";
      var objectKey = objectPrefix + CSV_FILE_NAME;
      var objectMetadata = new ObjectMetadata();
      objectMetadata.setContentLength(Files.readAllBytes(tempFile).length);
      objectMetadata.setContentType("text/csv");
      oppertunitiesS3Client.putObject(oppertunitiesS3Config.getBucket(), objectKey, fileStream,
          objectMetadata);
      log.info("Successfully uploaded oppertunies file to S3: {}", objectKey);
      // Delete the temporary file
      Files.delete(tempFile);
    } catch (IOException e) {
      log.error("Error in transfer oppertunies to S3", e);
    }
  }
}
