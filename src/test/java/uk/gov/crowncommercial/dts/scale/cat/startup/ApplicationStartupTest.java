package uk.gov.crowncommercial.dts.scale.cat.startup;

import static org.junit.jupiter.api.Assertions.fail;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * This test will pass if the Spring context and application start successfully.
 * Otherwise, it will fail automatically if there’s any kind of startup error, preventing the app from starting.
 * It also logs diagnostic info to assist debugging.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@Slf4j
class ApplicationStartupTest {

  @Test
  void contextLoads() {
    try {
      // Passed. Nothing to do here – context will auto-load when the test runs.
    } catch (Throwable ex) {
      Throwable rootCause = findRootCause(ex);

      log.error("Application context failed to start during ApplicationStartupTest");
      log.error("Root cause: [{}] {}", rootCause.getClass().getName(), rootCause.getMessage(), rootCause);
      log.error("Check configuration files, environment variables, bean definitions, and profile-specific settings.");

      fail("Application context failed to load: " + rootCause.getClass().getSimpleName() + " - " + rootCause.getMessage(), ex);
    }
  }

  private Throwable findRootCause(Throwable throwable) {
    Throwable cause = throwable.getCause();
    if (cause == null || cause == throwable) {
      return throwable;
    }
    return findRootCause(cause);
  }
}
