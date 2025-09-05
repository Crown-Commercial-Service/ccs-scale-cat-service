package uk.gov.crowncommercial.dts.scale.cat.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.controller.EventsController;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.service.DocGenService;
import uk.gov.crowncommercial.dts.scale.cat.service.EventTransitionService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentScoreExportService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import java.security.Principal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class, OAuth2Config.class})
@WebMvcTest(EventsController.class)
class ExceptionHandlerTests {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    private TendersAPIModelUtils tendersAPIModelUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProcurementEventService procurementEventService;

    @MockitoBean
    private EventTransitionService eventTransitionService;

    @MockitoBean
    private DocGenService docGenService;

    @MockitoBean
    private AssessmentScoreExportService exportService;

    @MockitoBean
    private Principal principal;

    @MockitoBean
    private EventSummary eventSummary;

    @MockitoBean
    private LockProvider lockProvider;

    // IF the app has successfully started, these endpoints will return 401 Unauthorised, and therefore this suit will pass. IF the app fails to start this test suit will fail.
    @Test
    void testNonExistentPathReturnsUnauthorised() throws Exception {
        mockMvc.perform(get("/doesnotexist")).andExpect(status().isUnauthorized());
    }

    @Test
    void testIndexReturnsUnauthorised() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
    }
}
