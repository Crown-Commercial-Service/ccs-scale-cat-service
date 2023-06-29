package uk.gov.crowncommercial.dts.scale.cat.service.data.transparancy;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.QuestionAndAnswerRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

/**
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectsGenerationScheduledTask {

  private ProcurementEventRepo procurementEventRepo;
  private AgreementsService agreementsService;
  private ConclaveService conclaveService;
  private JaggaerService jaggaerService;
  private QuestionAndAnswerRepo questionAndAnswerRepo;
  private final ObjectMapper objectMapper;
  private Environment env;
  private static final String DOS6_AGREEMENT_ID = "RM1043.8";
  private static final String AWARD_STATUS = "complete";
  private static final String JAGGAER_SUPPLIER_WINNER_STATUS = "Successful";

//  @Scheduled(fixedDelayString = "PT1M")
  @Transactional
  public void generateCSV() {
    log.info("Successfully started Projects CSV generation");
    writeEmployeesToCsv();
  }

  public void writeEmployeesToCsv() {

    Set<ProcurementEvent> events =
        procurementEventRepo.findEventsByTenderStatusAndAgreementId("planning", DOS6_AGREEMENT_ID);
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
            event.getPublishDate(),
            ChronoUnit.DAYS.between(event.getPublishDate(), event.getCloseDate()),
            getExpectedContractLength(event), getBudgetRangeData(event), "Applications from SMEs",
            "Applications from Large Organisations", "Total Organisations",
            defineStatus(event.getTenderStatus()), getWinningSupplier(event), "Size of supplier",
            " ", this.geContractStartData(event),
            questionAndAnswerRepo.countByEventId(event.getId()), "Employment status");

        csvPrinter.flush();
        csvPrinter.close();
      }
      // transferToS3(tempFile);
    } catch (IOException e) {
      log.error("Error While generating Projects CSV ", e);
    }
  }

  private String defineStatus(String status) {
    if (status.equals(AWARD_STATUS)) {
      return "Closed";
    }
    return "Open";
  }

  private String getWinningSupplier(final ProcurementEvent event) {
    if (event.getTenderStatus() == AWARD_STATUS) {
      ExportRfxResponse rfxWithSuppliers =
          jaggaerService.getRfxWithSuppliers(event.getExternalEventId());
      Optional<Supplier> winningSupplier = rfxWithSuppliers.getSuppliersList().getSupplier()
          .stream().filter(e -> e.getStatus().equals(JAGGAER_SUPPLIER_WINNER_STATUS)).findFirst();
      if (winningSupplier.isPresent()) {
        return winningSupplier.get().getCompanyData().getName();
      }
    }
    return "";
  }

  private String getExpectedContractLength(final ProcurementEvent event) {
    // lot 1 & lot 3-> Contract Length
    return getDataFromJSONDataTemplate(event,
        "$.criteria[?(@.id == 'Criterion 3')].requirementGroups[?(@.OCDS.id == 'Group 18')].OCDS.requirements[?(@.OCDS.id == 'Question 12')].nonOCDS.options[*].value");
  }


  private String geContractStartData(final ProcurementEvent event) {
    if (event.getProject().getLotNumber() == "1") {
      return getDataFromJSONDataTemplate(event,
          "$.criteria[?(@.id == 'Criterion 1')].requirementGroups[?(@.OCDS.id == 'Key Dates')].OCDS.requirements[?(@.OCDS.id == 'Question 13')].nonOCDS.options[*].value");
    }
    return getDataFromJSONDataTemplate(event,
        "$.criteria[?(@.id == 'Criterion 1')].requirementGroups[?(@.OCDS.id == 'Key Dates')].OCDS.requirements[?(@.OCDS.id == 'Question 11')].nonOCDS.options[*].value");
  }

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

    if (!StringUtils.isBlank(minValue) & !StringUtils.isBlank(minValue)) {
      return "£" + minValue + "-£" + maxValue;
    } else if (!StringUtils.isBlank(maxValue)) {
      return "up to £" + maxValue;
    } else
      return "not prepared to share details";
  }

  private String getDataFromJSONDataTemplate(final ProcurementEvent event,
      final String sourcePath) {
    var eventData = event.getProcurementTemplatePayloadRaw();
    var jsonPathConfig =
        Configuration.builder().options(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST)
            .jsonProvider(new JacksonJsonProvider(objectMapper))
            .mappingProvider(new JacksonMappingProvider(objectMapper)).build();
    TypeRef<String> typeRef = new TypeRef<>() {};
    return JsonPath.using(jsonPathConfig).parse(eventData).read(sourcePath, typeRef);
  }

  // private void transferToS3(Path tempFile) {
  // InputStream inputStream = Files.newInputStream(tempFile);
  // var filename = DateTimeFormatter.ISO_DATE_TIME
  // .format(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)).replace(":", "") + ".csv";
  // var objectKey = "oppertunity_data" + filename;
  // try {
  // // Upload the file to S3
  // InputStream fileStream = Files.newInputStream(tempFile);
  // var objectMetadata = new ObjectMetadata();
  // objectMetadata.setContentLength(Files.readAllBytes(tempFile).length);
  // objectMetadata.setContentType("text/csv");
  //
  // s3client.putObject("bucket name", objectKey, inputStream, objectMetadata);
  // log.info("Put file in S3: {}", objectKey);
  // // Delete the temporary file
  // Files.delete(tempFile);
  // } catch (IOException e) {
  // log.error("Error in transfer to S3", e);
  // }
  // }
}
