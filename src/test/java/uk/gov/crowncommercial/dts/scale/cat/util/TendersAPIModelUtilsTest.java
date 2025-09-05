package uk.gov.crowncommercial.dts.scale.cat.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Errors;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

@SpringBootTest(classes = {TendersAPIModelUtils.class, JaggaerAPIConfig.class},
    webEnvironment = WebEnvironment.NONE)
class TendersAPIModelUtilsTest {

  private static final String ERROR_STATUS = "error status";
  private static final String ERROR_TITLE = "error title";
  private static final String ERROR_MESSAGE = "error message";

  @MockitoBean
  ApplicationFlagsConfig appFlagsConfig;

  @Autowired
  TendersAPIModelUtils utils;

  @Test
  void testBuildDefaultErrors() throws Exception {

    Errors errors = utils.buildDefaultErrors(ERROR_STATUS, ERROR_TITLE, ERROR_MESSAGE);

    assertEquals(1, errors.getErrors().size());
    assertEquals(ERROR_STATUS, errors.getErrors().get(0).getStatus());
    assertEquals(ERROR_TITLE, errors.getErrors().get(0).getTitle());
    assertEquals(ERROR_MESSAGE, errors.getErrors().get(0).getDetail());
  }

}
