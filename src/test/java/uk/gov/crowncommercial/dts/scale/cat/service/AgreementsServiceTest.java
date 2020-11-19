package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import java.net.URI;
import javax.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.AgreementSummary;

/**
 * Service layer tests
 */
@SpringBootTest(classes = {AgreementsServiceConfig.class, AgreementsService.class})
public class AgreementsServiceTest {

  @Autowired
  private AgreementsService agreementService;

  @Autowired
  private RestTemplate restTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockRestServiceServer mockServer;

  @PostConstruct
  public void init() {
    mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  public void testFindAll()
      throws Exception {

    final AgreementSummary[] svcAgreementSummaries =
      {new AgreementSummary("RM3733", "Technology Products 2"),
          new AgreementSummary("RM6068", "Technology Products & Associated Services")};

    mockServer.expect(ExpectedCount.once(), requestTo(new URI("http://localhost:9010/agreements")))
    .andExpect(method(HttpMethod.GET)).andExpect(header("x-api-key", "abc123"))
    .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
        .body(objectMapper.writeValueAsString(svcAgreementSummaries)));

    final AgreementSummary[] agreementSummaries = agreementService.findAll();
    mockServer.verify();
    assertArrayEquals(svcAgreementSummaries, agreementSummaries);
  }

}
