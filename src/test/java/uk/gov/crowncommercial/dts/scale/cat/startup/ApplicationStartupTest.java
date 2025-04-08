package uk.gov.crowncommercial.dts.scale.cat.startup;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;

/**
 * This test will pass if the Spring context and application start successfully.
 * Otherwise, it will fail automatically if there’s any kind of startup error, preventing the app from starting.
 * Catches misconfigurations (missing properties, bad bean wiring, inconfigured profiles), or if anything throws during context load.
 */
@Slf4j
@SpringBootTest
class ApplicationStartupTest {

  @Test
  void contextLoads() {
    try {
      // Passed. Nothing to do here – context will auto-load when the test runs.
    } catch (Throwable ex) {
      // Failed - context failed to load.
      log.error("Application context failed to start during ApplicationStartupTest", ex);
      fail("Application context failed to load", ex);
    }
  }
}
