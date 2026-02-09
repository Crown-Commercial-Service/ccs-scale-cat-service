package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.service.MiService;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.MiQuestionAnswer;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/eprocurement/mi", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class MiController extends AbstractRestController {

    private final MiService miService;

    /**
     * Add Gcloud e-procurement details.
     */
    @PostMapping()
    @TrackExecutionTime
    public Integer addMiDetails(
            @RequestBody final  List<MiQuestionAnswer> miQuestionAnswer,
            final JwtAuthenticationToken authentication) {

        var principal = getPrincipalFromJwt(authentication);
        log.info("addGcloudEProcurementDetails invoked on behalf of principal: {}", principal);

        Integer results = 0;
        try {
            results = miService.createMiQuestionAndAnswer(miQuestionAnswer, principal);
        } catch (Exception ex) {
            log.error("Failed to e-procurement details into the cas db.", ex);
        }

        return results;
    }

    /**
     * Retrieve Gcloud e-procurement details.
     */
    @GetMapping()
    @TrackExecutionTime
    public List<MiQuestionAnswer> getMiDetails(final JwtAuthenticationToken authentication) {

        var principal = getPrincipalFromJwt(authentication);
        log.info("getGcloudEProcurementDetails invoked on behalf of principal: {}", principal);

        return miService.getMiDetails(principal);
    }
}
