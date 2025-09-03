package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserContactInfoList;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;

/**
 * Service layer tests
 */
@ExtendWith(MockitoExtension.class)
class ConclaveServiceTest {

  private static final String CONCLAVE_USER_ID = "12345";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient conclaveWebClient;

  @Mock
  private UserProfileResponseInfo userProfileResponseInfo;

  @Mock
  private WebclientWrapper webclientWrapper;

  @Mock
  private UserContactInfoList userContactInfoList;

  @Mock
  private ConclaveAPIConfig conclaveAPIConfig;

  @InjectMocks
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
