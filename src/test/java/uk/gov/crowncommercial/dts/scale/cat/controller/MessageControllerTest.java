package uk.gov.crowncommercial.dts.scale.cat.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import net.javacrumbs.shedlock.core.LockProvider;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.MessageRequestInfo;
import uk.gov.crowncommercial.dts.scale.cat.service.MessageService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Controller tests
 */
@WebMvcTest(MessageController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class, OAuth2Config.class})
@ActiveProfiles("test")
class MessageControllerTest {

    private static final String EVENTS_PATH = "/tenders/projects/{proc-id}/events";
    private static final String MESSAGES_PATH = "messages";
    private static final String PRINCIPAL = "jsmith@ccs.org.uk";
    private static final Integer PROC_PROJECT_ID = 1;
    private static final String EVENT_ID = "ocds-pfhb7i-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;
    
    @MockBean
    private LockProvider lockProvider;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validJwtReqPostProcessor;

    @BeforeEach
    void beforeEach() {
        validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
                .jwt(jwt -> jwt.subject(PRINCIPAL));
    }

    @Test
    void getMessages_200_OK() throws Exception {

    var messageRequestInfo =
        MessageRequestInfo.builder()
            .procId(PROC_PROJECT_ID)
            .eventId(EVENT_ID)
            .messageDirection(MessageDirection.RECEIVED)
            .messageRead(MessageRead.ALL)
            .messageSort(MessageSort.DATE)
            .messageSortOrder(MessageSortOrder.ASCENDING)
            .page(1)
            .pageSize(Integer.parseInt(Constants.UNLIMITED_VALUE))
            .principal(PRINCIPAL)
            .build();

        when(messageService.getMessagesSummary(messageRequestInfo))
                .thenReturn(new MessageSummary()
                        .messages(Arrays.asList(new CaTMessage()
                                .OCDS(new CaTMessageOCDS().title("Test")
                                        .author(new CaTMessageOCDSAllOfAuthor().id("test").name("Test Author")))
                                        .nonOCDS(new CaTMessageNonOCDS()
                                                .read(false).direction(CaTMessageNonOCDS.DirectionEnum.SENT)
                                                .isBroadcast(false)
                                                .receiverList(Collections.emptyList()))
                                ))
                        .counts(new MessageTotals().pageTotal(10).messagesTotal(100))
                        .links(new Links1().first(new URI( "/first/"))
                                .last(new URI( "/last/")).next(new URI( "/next/"))));

        mockMvc
                .perform(get(EVENTS_PATH + "/{event-id}/"+MESSAGES_PATH, PROC_PROJECT_ID,EVENT_ID)
                        .with(validJwtReqPostProcessor)
                        .accept(APPLICATION_JSON))
                .andDo(print()).andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.messages[0].ocds.author.name", is("Test Author")))
                .andExpect(jsonPath("$.links.next", is("/next/")));

        verify(messageService, times(1)).getMessagesSummary(messageRequestInfo);
    }
}