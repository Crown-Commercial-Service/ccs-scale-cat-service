package uk.gov.crowncommercial.dts.scale.cat.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
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
  private final ObjectMapper objectMapper;
  private final Environment env;
  private final AmazonS3 oppertunitiesS3Client;
  private final OppertunitiesS3Config oppertunitiesS3Config;
  private static final String DOS6_AGREEMENT_ID = "RM1043.8";
  private static final String AWARD_STATUS = "complete";
  private static final Integer JAGGAER_SUPPLIER_WINNER_STATUS = 3;
  private static final String PERIOD_FMT = "%d years, %d months, %d days";
  private static final String CSV_FILE_NAME = "oppertunity_data.csv";

  @Scheduled(cron = "${config.external.s3.oppertunities.schedule}")
  @Transactional
  public void generateCSV() {
    log.info("Started oppertunities CSV generation");
    writeOppertunitiesToCsv();
  }

  public void writeOppertunitiesToCsv() {

    Set<ProcurementEvent> events = retryableTendersDBDelegate
        .findEventsByTenderStatusAndAgreementId("planning", DOS6_AGREEMENT_ID);

    log.info("Dos6 agreements count: {}", events.size());
    AgreementDetail agreementDetails = agreementsService.getAgreementDetails(DOS6_AGREEMENT_ID);

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

        csvPrinter.printRecord(event.getProject().getId(), event.getProject().getProjectName(),
            env.getProperty("link"), agreementDetails.getName(), lotName, orgName, domainName,
            event.getPublishDate(), getOpenForCount(event), getExpectedContractLength(event),
            getBudgetRangeData(event), "Applications from SMEs",
            "Applications from Large Organisations", "Total Organisations",
            defineStatus(event.getTenderStatus()), getWinningSupplier(event), "Size of supplier",
            " ", this.geContractStartData(event),
            retryableTendersDBDelegate.findQuestionsCountByEventId(event.getId()),
            "Employment status");
      }
      csvPrinter.flush();
      csvPrinter.close();

      transferToS3(tempFile);

      log.info("Successfully generated CSV data");
    } catch (IOException e) {
      log.error("Error While generating Projects CSV ", e);
    }
  }

  private Long getOpenForCount(ProcurementEvent event) {
    if (Objects.nonNull(event.getPublishDate()) && Objects.nonNull(event.getCloseDate())) {
      return ChronoUnit.DAYS.between(event.getPublishDate(), event.getCloseDate());
    }
    return null;
  }

  private String defineStatus(String status) {
    if (status.equals(AWARD_STATUS)) {
      return "Closed";
    }
    return "Open";
  }

  private String getWinningSupplier(final ProcurementEvent event) {
    try {
      if (event.getTenderStatus().equals(AWARD_STATUS)) {
        ExportRfxResponse rfxWithSuppliers =
            jaggaerService.getRfxWithSuppliers(event.getExternalEventId());
        Optional<Supplier> winningSupplier = rfxWithSuppliers.getSuppliersList().getSupplier()
            .stream().filter(e -> e.getStatusCode() == JAGGAER_SUPPLIER_WINNER_STATUS).findFirst();
        if (winningSupplier.isPresent()) {
          return winningSupplier.get().getCompanyData().getName();
        }
      }
    } catch (Exception e) {
      // TODO: handle exception
    }
    return "";
  }

  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  private String getExpectedContractLength(final ProcurementEvent event) {
    // lot 1 & lot 3-> Contract Length
    try {
      String dataFromJSONDataTemplate = getDataFromJSONDataTemplate(event,
          "$.criteria[?(@.id == 'Criterion 3')].requirementGroups[?(@.OCDS.id == 'Group 18')].OCDS.requirements[?(@.OCDS.id == 'Question 12')].nonOCDS.options[*].value");
      if (Objects.nonNull(dataFromJSONDataTemplate)) {
        var period = Period.parse(dataFromJSONDataTemplate);
        return String.format(PERIOD_FMT, period.getYears(), period.getMonths(), period.getDays());
      }
    } catch (DateTimeParseException e) {
      // TODO: handle exception
    }
    return "";
  }

  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  private String geContractStartData(final ProcurementEvent event) {
    if (event.getProject().getLotNumber() == "1") {
      return getDataFromJSONDataTemplate(event,
          "$.criteria[?(@.id == 'Criterion 1')].requirementGroups[?(@.OCDS.id == 'Key Dates')].OCDS.requirements[?(@.OCDS.id == 'Question 13')].nonOCDS.options[*].value");
    }
    return getDataFromJSONDataTemplate(event,
        "$.criteria[?(@.id == 'Criterion 1')].requirementGroups[?(@.OCDS.id == 'Key Dates')].OCDS.requirements[?(@.OCDS.id == 'Question 11')].nonOCDS.options[*].value");
  }

  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  private String getBudgetRangeData(final ProcurementEvent event) {
    String maxValue = null;
    String minValue = null;

    if (event.getProject().getLotNumber() == "1") {
      maxValue = getDataFromJSONDataTemplate(event,
          "$.criteria[?(@.id == 'Criterion 3')].requirementGroups[?(@.OCDS.id == 'Group 20')].OCDS.requirements[?(@.OCDS.id == 'Question 2')].nonOCDS.options[?(@.select == true)].value");

      minValue = getDataFromJSONDataTemplate(event,
          "$.criteria[?(@.id == 'Criterion 3')].requirementGroups[?(@.OCDS.id == 'Group 20')].OCDS.requirements[?(@.OCDS.id == 'Question 3')].nonOCDS.options[?(@.select == true)].value");
    }
    if (event.getProject().getLotNumber() == "3") {
      maxValue = getDataFromJSONDataTemplate(event,
          "$.criteria[?(@.id == 'Criterion 3')].requirementGroups[?(@.OCDS.id == 'Group 18')].OCDS.requirements[?(@.OCDS.id == 'Question 2')].nonOCDS.options[?(@.select == true)].value");

      minValue = getDataFromJSONDataTemplate(event,
          "$.criteria[?(@.id == 'Criterion 3')].requirementGroups[?(@.OCDS.id == 'Group 18')].OCDS.requirements[?(@.OCDS.id == 'Question 3')].nonOCDS.options[?(@.select == true)].value");
    }
    log.info("MaxValue: {} - MinValue: {}", maxValue, minValue);

    if (!StringUtils.isBlank(minValue) & !StringUtils.isBlank(minValue)) {
      return "£" + minValue + "-£" + maxValue;
    } else if (!StringUtils.isBlank(maxValue)) {
      return "up to £" + maxValue;
    } else
      return "not prepared to share details";
  }

  private String getDataFromJSONDataTemplate(final ProcurementEvent event,
      final String sourcePath) {
    try {
      var eventData = event.getProcurementTemplatePayloadRaw();
      var jsonPathConfig =
          Configuration.builder().options(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST)
              .jsonProvider(new JacksonJsonProvider(objectMapper))
              .mappingProvider(new JacksonMappingProvider(objectMapper)).build();
      TypeRef<List<String>> typeRef = new TypeRef<>() {};
      List<String> read = JsonPath.using(jsonPathConfig).parse(eventData).read(sourcePath, typeRef);
      log.info(read.toString());
      return read.get(0);
    } catch (Exception e) {
      // TODO: handle exception
    }
    return null;
  }

  private void transferToS3(Path tempFile) throws IOException {
    var objectKey = CSV_FILE_NAME;
    try {
      InputStream fileStream = Files.newInputStream(tempFile);
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
