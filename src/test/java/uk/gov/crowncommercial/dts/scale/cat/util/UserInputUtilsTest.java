package uk.gov.crowncommercial.dts.scale.cat.util;

import org.junit.jupiter.api.Test;
import uk.gov.crowncommercial.dts.scale.cat.utils.UserInputUtils;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserInputUtilsTest {


    @Test
    void testForString() throws Exception {
    assertEquals("1234", Pattern.quote("1234"));
    }
}