package uk.gov.crowncommercial.dts.scale.cat.util;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.crowncommercial.dts.scale.cat.utils.SanitisationUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {SanitisationUtils.class}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class SanitisationUtilsTest {
    private final String userEmail = "test@testmail.com";
    private final String scriptString = "<script>alert('Hello');</script>";
    private final String boldString = "<b>Hello</b>";

    @Autowired
    private SanitisationUtils sanitisationUtils;

    @Test
    void testNormalStringSanitisesOkAsText() throws Exception {
        String output = sanitisationUtils.sanitiseStringAsText(userEmail);

        assertNotNull(output);
        assertEquals(userEmail, output);
    }

    @Test
    void testBoldStringSanitisesOkAsText() throws Exception {
        String output = sanitisationUtils.sanitiseStringAsText(boldString);

        assertNotNull(output);
        assertEquals("Hello", output);
    }

    @Test
    void testScriptStringSanitisesOkAsText() throws Exception {
        String output = sanitisationUtils.sanitiseStringAsText(scriptString);

        assertNotNull(output);
        assertEquals("", output);
    }

    @Test
    void testNormalStringSanitisesOkAsFormattedText() throws Exception {
        String output = sanitisationUtils.sanitiseStringAsFormattedText(userEmail);

        assertNotNull(output);
        assertEquals(userEmail, output);
    }

    @Test
    void testBoldStringSanitisesOkAsFormattedText() throws Exception {
        String output = sanitisationUtils.sanitiseStringAsFormattedText(boldString);

        assertNotNull(output);
        assertEquals(boldString, output);
    }

    @Test
    void testScriptStringSanitisesOkAsFormattedText() throws Exception {
        String output = sanitisationUtils.sanitiseStringAsFormattedText(scriptString);

        assertNotNull(output);
        assertEquals("", output);
    }
}
