package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;

@SpringBootTest(classes = {EncryptionService.class}, webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties({RPAAPIConfig.class})
@ActiveProfiles("test")
class EncryptionServiceTest {

  @Autowired
  EncryptionService eService;

  @Test
  void testEncryptPassword() throws Exception {
    String encPassword = eService.encryptPassword("Venki");
    String dePwd = eService.decryptPassword(encPassword);
    assertAll(() -> assertNotNull(dePwd), () -> assertNotNull(encPassword),
        () -> assertEquals("Venki", dePwd));
  }

}
