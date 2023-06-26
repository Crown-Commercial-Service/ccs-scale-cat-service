package uk.gov.crowncommercial.dts.scale.cat.service.data.transparancy;

import java.io.IOException;
import java.io.Writer;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;

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
  private Environment env;

  private static final String FRAMEWORK = "Digital-Outcomes-And-Specialists";
  private static final String AGREEMENT_ID = "RM1043.8";

  @Scheduled(fixedDelayString = "PT1M")
  @Transactional
  void generateCSV() {
    log.info("Successfully started Projects CSV generation");

  }

  public void writeEmployeesToCsv(Writer writer) {

    Set<ProcurementEvent> events = procurementEventRepo.findByTenderStatus("planning");
    AgreementDetail agreementDetails = agreementsService.getAgreementDetails(AGREEMENT_ID);
    try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
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
              agreementsService.getLotDetails(AGREEMENT_ID, event.getProject().getLotNumber());
          lotName = lotDetails.getName();
          Optional<OrganisationProfileResponseInfo> organisationIdentity =
              conclaveService.getOrganisationIdentity(
                  event.getProject().getOrganisationMapping().getOrganisationId());
          orgName = organisationIdentity.get().getIdentifier().getLegalName();
          domainName = organisationIdentity.get().getIdentifier().getUri();
        } catch (Exception e) {
          log.error(e.getMessage());
        }

        csvPrinter.printRecord(event.getProject().getId(), event.getProject().getProjectName(),
            env.getProperty("link"), agreementDetails.getName(), lotName, orgName, domainName,
            event.getPublishDate(),
            ChronoUnit.DAYS.between(event.getPublishDate(), event.getCloseDate()),
            "Expected Contract Length", "Budget range", "Applications from SMEs",
            "Applications from Large Organisations", "Total Organisations", "Status",
            "Winning supplier", "Size of supplier", "Contract amount", "Contract start date",
            "Clarification questions", "Employment status");
      }
    } catch (IOException e) {
      log.error("Error While generating Projects CSV ", e);
    }
  }

}
