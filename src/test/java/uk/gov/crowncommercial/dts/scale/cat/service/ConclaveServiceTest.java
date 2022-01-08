package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserContactInfoList;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;

/**
 * Service layer tests
 */
@SpringBootTest(classes = {ConclaveService.class, ConclaveAPIConfig.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(ConclaveAPIConfig.class)
class ConclaveServiceTest {

  private static final String CONCLAVE_USER_ID = "12345";

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient conclaveWebClient;

  @MockBean
  private UserProfileResponseInfo userProfileResponseInfo;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean
  private UserContactInfoList userContactInfoList;

  @Autowired
  private ConclaveAPIConfig conclaveAPIConfig;

  @Autowired
  private ConclaveService conclaveService;

  @Test
  void testGetUserProfile() {

    // Mock behaviours
    when(webclientWrapper.getOptionalResource(UserProfileResponseInfo.class, conclaveWebClient,
        conclaveAPIConfig.getTimeoutDuration(), conclaveAPIConfig.getGetUser().get("uriTemplate"),
        CONCLAVE_USER_ID)).thenReturn(Optional.of(userProfileResponseInfo));

    // Invoke
    var userProfile = conclaveService.getUserProfile(CONCLAVE_USER_ID);

    // Verify
    assertEquals(Optional.of(userProfileResponseInfo), userProfile);
  }

  @Test
  void testGetUserContacts() {

    // Mock behaviours
    when(webclientWrapper.getOptionalResource(UserContactInfoList.class, conclaveWebClient,
        conclaveAPIConfig.getTimeoutDuration(),
        conclaveAPIConfig.getGetUserContacts().get("uriTemplate"), CONCLAVE_USER_ID))
            .thenReturn(Optional.of(userContactInfoList));

    // Invoke
    var userContacts = conclaveService.getUserContacts(CONCLAVE_USER_ID);

    // Verify
    assertEquals(userContactInfoList, userContacts);
  }

}
