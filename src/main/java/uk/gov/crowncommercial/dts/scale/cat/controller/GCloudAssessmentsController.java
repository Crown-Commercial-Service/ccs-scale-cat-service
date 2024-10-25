package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.GCloudAssessmentSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.GCloudAssessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.GCloudResult;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.GCloudAssessmentService;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/assessments", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class GCloudAssessmentsController extends AbstractRestController {
    private static final String CSV_GENERIC_HEADERS = "Framework name,Search ended,Search criteria\n";
    private static final String CSV_STATIC_NAME = "G-Cloud 13";
    private static final String CSV_RESULTS_HEADERS = "\nSupplier name,Service name,Service description,Service page URL\n";
    private static final String CSV_DATE_FORMAT = "EEEE dd MMMM y h:m zzz";

    private final GCloudAssessmentService assessmentService;

    private final AssessmentService coreAssessmentService;

    private final AgreementsService agreementsService;

    /**
     * Creates a new Gcloud assessment that the user will score suppliers based on requirements for an event within a lot.
     */
    @PostMapping("/gcloud")
    @TrackExecutionTime
    public Integer createGcloudAssessment(@RequestBody final GCloudAssessment assessment, final JwtAuthenticationToken authentication) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("createGcloudAssessment invoked on behalf of principal: {}", principal);

        return assessmentService.createGcloudAssessment(assessment, principal);
    }

    /**
     * Retrieves requested Gcloud assessment
     */
    @GetMapping("/{assessment-id}/gcloud")
    @TrackExecutionTime
    public GCloudAssessment getGcloudAssessment(final @PathVariable("assessment-id") Integer assessmentId, final JwtAuthenticationToken authentication) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("getGcloudAssessment invoked on behalf of principal: {}", principal);

        return assessmentService.getGcloudAssessment(assessmentId);
    }

    /**
     * Updates a saved Gcloud assessment
     */
    @PutMapping("/{assessment-id}/gcloud")
    @TrackExecutionTime
    public void updateGcloudAssessment(@RequestBody final GCloudAssessment assessment, final @PathVariable("assessment-id") Integer assessmentId, final JwtAuthenticationToken authentication) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("updateGcloudAssessment invoked on behalf of principal: {}", principal);

        assessmentService.updateGcloudAssessment(assessment, assessmentId, principal);
    }

    /**
     * Gets a list of GCloud Assessment Summaries for a user
     */
    @GetMapping("/gcloud/summaries")
    @TrackExecutionTime
    public List<GCloudAssessmentSummary> getGcloudAssessmentSummaries(final JwtAuthenticationToken authentication) {
        List<GCloudAssessmentSummary> model = new ArrayList<>();

        // First get the principal from the token and use it to fetch a list of all GCloud assessments for the user
        String principal = getPrincipalFromJwt(authentication);
        List<AssessmentSummary> userAssessments = coreAssessmentService.getAssessmentsForUser(principal, 14);

        if (userAssessments != null && !userAssessments.isEmpty()) {
            // Now for each assessment we've found we need to build the GCloudAssessmentSummary model and add it to our results
            userAssessments.stream().forEach(result -> {
                GCloudAssessmentSummary summaryModel = assessmentService.getGcloudAssessmentSummary(result.getAssessmentId());

                if (summaryModel != null) {
                    model.add(summaryModel);
                }
            });
        }

        // Results should now have been built up, so return our list
        return model;
    }

    /**
     * Exports the results of a requested GCloud Assessment
     */
    @GetMapping(produces="text/csv", path="/{assessment-id}/export/gcloud")
    @TrackExecutionTime
    public void exportGcloudAssessment(final @PathVariable("assessment-id") Integer assessmentId, @RequestParam(name = "framework-id", required = false) final String frameworkId, final JwtAuthenticationToken authentication, HttpServletResponse response) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("exportGcloudAssessment invoked on behalf of principal: {}", principal);

        // We need to get the Gcloud Assessment model to begin with
        GCloudAssessment assessmentModel = assessmentService.getGcloudAssessment(assessmentId);

        if (assessmentModel != null) {
            // We have the model, but before we do anything with it we need to fetch the correct framework name
            String frameworkName = CSV_STATIC_NAME;

            if (frameworkId != null && !frameworkId.isEmpty()) {
                AgreementDetail agreementModel = agreementsService.getAgreementDetails(frameworkId);

                if (agreementModel != null && agreementModel.getName() != null && !agreementModel.getName().isEmpty()) {
                    frameworkName = agreementModel.getName();
                }
            }

            // Now we need to output the model as CSV in the specified format
            try {
                // Start by specifying the headers etc
                response.setContentType("text/csv");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setHeader("Content-Disposition", "attachment; filename=gcloud-assessment-export.csv");

                // Now write the contents of our document
                try (Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
                    // Start with the generic information
                    SimpleDateFormat dateFormat = new SimpleDateFormat(CSV_DATE_FORMAT);
                    String exportTime = dateFormat.format(new Date());
                    // Sanitise the results summary to remove the HTML tags

                    String sanitisedResultsSummary = StringUtils.normalizeSpace(Jsoup.parse(assessmentModel.getResultsSummary()).text());
                    if (sanitisedResultsSummary.contains(",") || sanitisedResultsSummary.contains("\"") || sanitisedResultsSummary.contains("'")) {
                        sanitisedResultsSummary = sanitisedResultsSummary.replace("\"", "\"\"");
                    }
                    writer.write(CSV_GENERIC_HEADERS);
                    writer.write(frameworkName + "," + exportTime + ",\"" + sanitisedResultsSummary + "\"\n");

                    // Now deal with the specific results
                    writer.write(CSV_RESULTS_HEADERS);

                    if (!assessmentModel.getResults().isEmpty()) {
                        for (GCloudResult result : assessmentModel.getResults()) {
                            writer.write(StringEscapeUtils.escapeCsv(result.getSupplier().getName()) + ",");
                            writer.write(StringEscapeUtils.escapeCsv(result.getServiceName()) + ",");
                            writer.write(StringEscapeUtils.escapeCsv(result.getServiceDescription()) + ",");
                            writer.write(StringEscapeUtils.escapeCsv(result.getServiceLink().toString()) + "\n");
                        }
                    }

                    writer.flush();
                }
            } catch (Exception ex) {
                log.error("Error exporting CSV for Gcloud Assessment", ex);
            }
        }
    }

    /**
     * Deletes a specified GCloud Assessment
     */
    @DeleteMapping("/{assessment-id}")
    @TrackExecutionTime
    public void deleteGcloudAssessment(final @PathVariable("assessment-id") Integer assessmentId, final JwtAuthenticationToken authentication) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("deleteGcloudAssessment invoked on behalf of principal: {}", principal);

        assessmentService.deleteGcloudAssessment(assessmentId);
    }
}
