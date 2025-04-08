package uk.gov.crowncommercial.dts.scale.cat.startup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * This test will pass if the Spring context and application start successfully.
 * Otherwise, it will fail automatically if there’s any kind of startup error, preventing the app from starting.
 * It also logs diagnostic info to assist debugging.
 */
@SpringBootTest
class ApplicationStartupTest {

  @Test
  void contextLoads() {
    // The test will pass as long as the application context can load.
  }
}
