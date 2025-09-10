package uk.gov.crowncommercial.dts.scale.cat.controller;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.SupplierDunsUpdate;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Controller to handle requests for supplier data outside of given projects / events
 */
@RestController
@RequestMapping(path = "/suppliers", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class SuppliersController extends AbstractRestController {
    /**
     * Update the DUNs Number of a given supplier across the various mapping tables in the DB
     */
    @PutMapping("/update-duns")
    public String updateSupplierDunsMappings(@RequestBody final SupplierDunsUpdate supplierIds, final JwtAuthenticationToken authentication) {
        // First, we need to validate the auth passed to use
        String principal = getPrincipalFromJwt(authentication);

        if (supplierIds != null && supplierIds.getCurrentDunsNumber() != null && supplierIds.getReplacementDunsNumber() != null) {
            // Request looks valid at initial glance, so look to try and process it
            // TODO: work
            //assessmentService.updateDimensions(assessmentId, dimensionRequirement, principal);

            // Return an indicator of the operation's success
            return Constants.OK_MSG;
        }

        // If we've reached this point, we were not supplied with the info we need to perform an update.  Return an error indicating this
        throw new ValidationException(Constants.ERR_MSG_INCOMPLETE_DATA);
    }
}