package uk.gov.crowncommercial.dts.scale.cat.utils;

import org.jsoup.Jsoup;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.StringUtils.trim;

/**
 * Holds utility methods for the sanitisation of data passed in for writing
 */
@Component
public class SanitisationUtils {
    /**
     * Sanitise a given String value
     */
    public String sanitiseString(String inputValue, Boolean htmlSupported) {
        // Instantiate the Jsoup cleaner
        Cleaner cleaner;

        // Check to see whether HTML needs to be supported in this input.  By default this should always be no
        if (htmlSupported) {
            cleaner = new Cleaner(Safelist.basic());
        } else {
            cleaner = new Cleaner(Safelist.none());
        }

        return cleaner.clean(Jsoup.parse(trim(inputValue))).text();
    }

    // TODO: Handling models
}
