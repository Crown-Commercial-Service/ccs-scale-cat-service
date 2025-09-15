package uk.gov.crowncommercial.dts.scale.cat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDunsUpdate {
    private String currentDunsNumber;
    private String replacementDunsNumber;

    /**
     * Returns the "Current DUNs Number" formatted with the correct DUNS prefix
     */
    public String getFormattedCurrentDunsNumber() {
        // In this case, we need to make sure that the value starts with the DUNS prefix
        if (currentDunsNumber != null && !currentDunsNumber.startsWith(Constants.DUNS_PREFIX)) {
            return Constants.DUNS_PREFIX + currentDunsNumber;
        }

        // Looks like the value is either null or already has the prefix - return it directly
        return currentDunsNumber;
    }

    /**
     * Returns the "Current DUNs Number" formatted without any prefix
     */
    public String getCleanedCurrentDunsNumber() {
        // In this case, we need to make sure that the value does not start with the DUNS prefix
        if (currentDunsNumber != null) {
            return currentDunsNumber.replace(Constants.DUNS_PREFIX, "");
        }

        // Looks like the value is null - return it directly
        return currentDunsNumber;
    }

    /**
     * Returns the "Replacement DUNs Number" formatted with the correct DUNS prefix
     */
    public String getFormattedReplacementDunsNumber() {
        // In this case, we need to make sure that the value starts with the DUNS prefix
        if (replacementDunsNumber != null && !replacementDunsNumber.startsWith(Constants.DUNS_PREFIX)) {
            return Constants.DUNS_PREFIX + replacementDunsNumber;
        }

        // Looks like the value is either null or already has the prefix - return it directly
        return replacementDunsNumber;
    }

    /**
     * Returns the "Replacement DUNs Number" formatted without any prefix
     */
    public String getCleanedReplacementDunsNumber() {
        // In this case, we need to make sure that the value does not start with the DUNS prefix
        if (replacementDunsNumber != null) {
            return replacementDunsNumber.replace(Constants.DUNS_PREFIX, "");
        }

        // Looks like the value is null - return it directly
        return replacementDunsNumber;
    }
}