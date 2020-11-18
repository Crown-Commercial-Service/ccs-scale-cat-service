package uk.gov.crowncommercial.dts.scale.cat;

import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.rollbar.notifier.Rollbar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class Application {

  private final Rollbar rollbar;

  @Value("${AGREEMENTS_SERVICE_URL:http://localhost:9010}")
  private String agrementsServiceUrl;

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @PostConstruct
  public void init() {
    log.info("AGREEMENTS_SERVICE_URL=" + agrementsServiceUrl);
    rollbar.debug("CCS Scale CaT API service started...");
  }

}
