package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.exception.NotSupportedException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.*;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.SupplierScore;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ValidationService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Predicate;


@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentScoreExportService {
    private final AssessmentService assessmentService;
    private final AgreementsService agreementService;

    private final ValidationService validationService;
    private final ObjectMapper mapper;

    private static final String SUPPLIER_SCORE = "Supplier_Score_%s_%s.csv";
    private static final String NOT_SUPPORTED_MIME_TYPE = "Mime Type application/json not supported";

    public List<SupplierScore> getScores(final Integer projectId, final String eventId,
                                         final Float minScore, final Float maxScore,
                                         final Optional<String> principalForScores) {


        ProcurementEvent event = validationService.validateProjectAndEventIds(projectId, eventId);
        Integer assessmentId = event.getAssessmentId();
        if (null == assessmentId) {
            throw new ResourceNotFoundException("AssessmentId not found for project " + projectId + ", eventId " + eventId);
        }

        ProcurementProject project = event.getProject();
        Assessment assessment = assessmentService.getAssessment(assessmentId, true, principalForScores);
        List<SupplierScores> calculatedScores = assessment.getScores();


        Collection<LotSupplier> suppliers = agreementService.getLotSuppliers(project.getCaNumber(), project.getLotNumber());
        Map<String, LotSupplier> supplierMap = new HashMap<>(suppliers.size() * 2);
        for (LotSupplier supplier : suppliers) {
            supplierMap.put(supplier.getOrganization().getId(), supplier);
        }


        Predicate<SupplierScores> filter = getFilter(minScore, maxScore);

        List<SupplierScore> scoreList = calculatedScores.stream().filter(filter)

                .map(d -> convert(d, supplierMap))
                .toList();
        return scoreList;
    }

    public ResponseEntity<InputStreamResource> export(
            final Integer projectId, final String eventId,
            final List<SupplierScore> supplierScores,
            final String mimeType) {
        if (mimeType.equalsIgnoreCase("text/csv"))
            return printToCsv(supplierScores, String.format(SUPPLIER_SCORE, projectId, eventId));
        else if (mimeType.equalsIgnoreCase(MediaType.APPLICATION_JSON_VALUE))
            return printToJson(supplierScores);
        else throw new NotSupportedException(NOT_SUPPORTED_MIME_TYPE);
    }


    public SupplierScore convert(SupplierScores input, Map<String, LotSupplier> supplierMap) {
        SupplierScore output = new SupplierScore();
        String supplierDunsNumber = input.getSupplier().getId();
        output.setScore(input.getTotal());
        output.setIdentifier(supplierDunsNumber);
        LotSupplier supplier = supplierMap.get(supplierDunsNumber);
        if (null != supplier) {
            Set<Contact> contacts = supplier.getLotContacts();
            if (null != contacts && contacts.size() > 0) {
                Iterator<Contact> itr = contacts.stream().iterator();
                while (itr.hasNext()) {
                    Contact ct = itr.next();
                    ContactPoint contact = ct.getContactPoint();
                    if (null != contact) {
                        output.setContactEmail(contact.getEmail());
                        output.setContactFaxNumber(contact.getFaxNumber());
                        output.setContactName(contact.getName());
                        output.setContactTelephone(contact.getTelephone());
                        output.setContactUrl(contact.getUrl());
                        break;
                    }
                }
            }

            Organization org = supplier.getOrganization();
            if (null != org) {
                Address address = org.getAddress();
                if (null != address) {
                    output.setLocality(address.getLocality());
                    output.setCountryName(address.getCountryName());
                    output.setStreetAddress(address.getStreetAddress());
                    output.setRegion(address.getRegion());
                    output.setPostalCode(address.getPostalCode());
                }

                output.setName(org.getName());
            }
        }
        return output;
    }

    private Predicate<SupplierScores> getFilter(Float minScore, Float maxScore) {

        if (null != minScore) {
            if (null != maxScore) {
                return new MinMaxScorePredicate(minScore, maxScore);
            } else
                return new MinScorePredicate(minScore);
        } else {
            if (null != maxScore)
                return new MaxScorePredicate(maxScore);
            else
                return new NoopPredicate();
        }
    }

    private static class MinScorePredicate implements Predicate<SupplierScores> {
        private final double minScore;

        public MinScorePredicate(double minScore) {
            this.minScore = minScore;
        }

        @Override
        public boolean test(SupplierScores supplierScores) {
            double score = supplierScores.getTotal();
            return score >= minScore;
        }
    }

    private static class MaxScorePredicate implements Predicate<SupplierScores> {
        private final double maxScore;

        public MaxScorePredicate(double maxScore) {
            this.maxScore = maxScore;
        }

        @Override
        public boolean test(SupplierScores supplierScores) {
            double score = supplierScores.getTotal();
            return score <= maxScore;
        }
    }

    private static class MinMaxScorePredicate implements Predicate<SupplierScores> {
        private final double maxScore, minScore;

        public MinMaxScorePredicate(double minScore, double maxScore) {
            this.maxScore = maxScore;
            this.minScore = minScore;
        }

        @Override
        public boolean test(SupplierScores supplierScores) {
            double score = supplierScores.getTotal();
            return score >= minScore && score <= maxScore;
        }
    }

    private static class NoopPredicate implements Predicate<SupplierScores> {
        @Override
        public boolean test(SupplierScores supplierScores) {
            return true;
        }
    }

    @SneakyThrows
    private ResponseEntity<InputStreamResource> printToJson(List<SupplierScore> supplierScores) {

        var out = new ByteArrayOutputStream();
        mapper.writer().writeValue(out, supplierScores);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InputStreamResource(new ByteArrayInputStream(out.toByteArray())));
    }

    private ResponseEntity<InputStreamResource> printToCsv(List<SupplierScore> supplierScores, String
            filename) {
        var out = new ByteArrayOutputStream();
        try (var csvPrinter = new CSVPrinter(new PrintWriter(out), CSVFormat.DEFAULT)) {

            csvPrinter.printRecord("name", "identifier", "streetAddress", "locality", "region", "postalCode",
                    "countryName", "contactName", "contactEmail", "contactTelephone", "contactFaxNumber",
                    "contactUrl", "score");
            for (SupplierScore calculationBase : supplierScores) {
                writeRecord(calculationBase, csvPrinter);
            }
            csvPrinter.flush();
        } catch (IOException e) {
            throw new RuntimeException("fail to import data to CSV file: " + e.getMessage());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(new InputStreamResource(new ByteArrayInputStream(out.toByteArray())));
    }


    private void writeRecord(final SupplierScore ssd, final CSVPrinter csvPrinter)
            throws IOException {
        csvPrinter.printRecord(ssd.getName(), ssd.getIdentifier(),
                ssd.getStreetAddress(), ssd.getLocality(), ssd.getRegion(),
                ssd.getPostalCode(), ssd.getCountryName(),
                ssd.getContactName(), ssd.getContactEmail(), ssd.getContactTelephone(),
                ssd.getContactFaxNumber(), ssd.getContactUrl(), ssd.getScore());

    }
}
