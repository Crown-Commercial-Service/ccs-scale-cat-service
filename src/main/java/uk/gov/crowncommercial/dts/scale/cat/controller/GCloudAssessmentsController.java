package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.GCloudAssessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.GCloudResult;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.GCloudAssessmentService;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/assessments", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class GCloudAssessmentsController extends AbstractRestController {
    private static final String CSV_GENERIC_HEADERS = "Framework name,Search ended,Search summary\n";
    private static final String CSV_STATIC_NAME = "G-Cloud 13";
    private static final String CSV_RESULTS_TEXT = " results found\n";
    private static final String CSV_RESULTS_HEADERS = "\nSupplier name,Service name,Service description,Service page URL\n";
    private static final String CSV_DATE_FORMAT = "EEEE dd MMMM y h:m zzz";

    private final GCloudAssessmentService assessmentService;

    /**
     * Creates a new Gcloud assessment that the user will score suppliers based on requirements for an event within a lot.
     */
    @PostMapping("/gcloud")
    public Integer createGcloudAssessment(@RequestBody final GCloudAssessment assessment, final JwtAuthenticationToken authentication) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("createGcloudAssessment invoked on behalf of principal: {}", principal);

        return assessmentService.createGcloudAssessment(assessment, principal);
    }

    /**
     * Retrieves requested Gcloud assessment
     */
    @GetMapping("/{assessment-id}/gcloud")
    public GCloudAssessment getGcloudAssessment(final @PathVariable("assessment-id") Integer assessmentId, final JwtAuthenticationToken authentication) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("getGcloudAssessment invoked on behalf of principal: {}", principal);

        return assessmentService.getGcloudAssessment(assessmentId);
    }

    /**
     * Updates a saved Gcloud assessment
     */
    @PutMapping("/{assessment-id}/gcloud")
    public void updateGcloudAssessment(@RequestBody final GCloudAssessment assessment, final @PathVariable("assessment-id") Integer assessmentId, final JwtAuthenticationToken authentication) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("updateGcloudAssessment invoked on behalf of principal: {}", principal);

        assessmentService.updateGcloudAssessment(assessment, assessmentId, principal);
    }

    /**
     * Exports the results of a requested GCloud Assessment
     */
    @GetMapping(produces="text/csv", path="/{assessment-id}/export/gcloud")
    public void exportGcloudAssessment(final @PathVariable("assessment-id") Integer assessmentId, final JwtAuthenticationToken authentication, HttpServletResponse response) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("exportGcloudAssessment invoked on behalf of principal: {}", principal);

        // We need to get the Gcloud Assessment model to begin with
        GCloudAssessment assessmentModel = assessmentService.getGcloudAssessment(assessmentId);

        if (assessmentModel != null) {
            // We have the model, now we need to output it as CSV in the specified format
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
                    writer.write(CSV_GENERIC_HEADERS);
                    writer.write(CSV_STATIC_NAME + "," + exportTime + "," + assessmentModel.getResults().size() + CSV_RESULTS_TEXT);

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
}
