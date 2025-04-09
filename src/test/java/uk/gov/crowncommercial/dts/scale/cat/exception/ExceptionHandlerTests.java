package uk.gov.crowncommercial.dts.scale.cat.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
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

@RunWith(SpringRunner.class)
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

    @MockBean
    private ProcurementEventService procurementEventService;

    @MockBean
    private EventTransitionService eventTransitionService;

    @MockBean
    private DocGenService docGenService;

    @MockBean
    private AssessmentScoreExportService exportService;

    @MockBean
    private Principal principal;

    @MockBean
    private EventSummary eventSummary;

    @MockBean
    private LockProvider lockProvider;

    @Test
    void testNonExistentPathReturns404() throws Exception {
        mockMvc.perform(get("/doesnotexist")).andExpect(status().isNotFound());
    }

    @Test
    void testIndexReturns200() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void testValidPageReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
