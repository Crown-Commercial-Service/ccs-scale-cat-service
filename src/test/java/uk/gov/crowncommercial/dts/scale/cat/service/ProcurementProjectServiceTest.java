package uk.gov.crowncommercial.dts.scale.cat.service;

import javax.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;

/**
 * Service layer tests
 */
@SpringBootTest(classes = {ProcurementProjectService.class, JaggaerAPIConfig.class},
    webEnvironment = WebEnvironment.NONE)
class ProcurementProjectServiceTest {

  @MockBean
  private WebClient jaggaerWebClient;

  @MockBean
  private ProcurementProjectRepo procurementProjectRepo;

  @MockBean
  private ProcurementEventService procurementEventService;

  @MockBean
  private JaggaerUserProfileService jaggaerUserProfileService;

  @Autowired
  private ProcurementProjectService procurementProjectService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockRestServiceServer mockServer;

  @PostConstruct
  public void init() {
    // mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  void testFindAll() throws Exception {

    // final AgreementSummary[] svcAgreementSummaries =
    // {new AgreementSummary("RM3733", "Technology Products 2"),
    // new AgreementSummary("RM6068", "Technology Products & Associated Services")};
    //
    // mockServer.expect(ExpectedCount.once(), requestTo(new
    // URI("http://localhost:9010/agreements")))
    // .andExpect(method(HttpMethod.GET)).andExpect(header("x-api-key", "abc123"))
    // .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
    // .body(objectMapper.writeValueAsString(svcAgreementSummaries)));
    //
    // final AgreementSummary[] agreementSummaries = agreementService.findAll();
    // mockServer.verify();
    // assertArrayEquals(svcAgreementSummaries, agreementSummaries);
  }

}
