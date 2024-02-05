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
     * Sanitise a given String value and returns as text
     */
    public String sanitiseStringAsText(String inputValue) {
        // Instantiate the Jsoup cleaner - we need to parse this too so it transforms encoded characters correctly
        Cleaner cleaner = new Cleaner(Safelist.none());

        return cleaner.clean(Jsoup.parse(trim(inputValue))).text();
    }

    /**
     * Sanitise a given String value and returns as "simple text" (aka. text with HTML elements)
     */
    public String sanitiseStringAsFormattedText(String inputValue) {
        // Instantiate the Jsoup cleaner - we can just sanitise this directly
        return Jsoup.clean(trim(inputValue), Safelist.simpleText());
    }
}
