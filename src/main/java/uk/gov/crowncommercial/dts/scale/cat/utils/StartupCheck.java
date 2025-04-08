package uk.gov.crowncommercial.dts.scale.cat.utils;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * This test will pass if the Spring context and application start successfully.
 * Otherwise, it will fail if there’s any startup errors, preventing the app from starting.
 * It also logs diagnostic info to assist debugging.
 */
@Component
@Slf4j
public class StartupCheck implements CommandLineRunner {

  @Override
  public void run(String... args) throws Exception {
    try {
      // App started without any critical errors
      log.info("CaS API started successfully.");
    } catch (Exception e) {
      // App was unable to start. Send alert into rollbar and also throw error to any clients.
      log.error("Error during CaS API startup: {}", e.getMessage(), e);
      throw new RuntimeException("Application failed to start", e);
    }
  }
}
