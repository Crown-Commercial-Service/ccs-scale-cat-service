package uk.gov.crowncommercial.dts.scale.cat;

import javax.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.rollbar.notifier.Rollbar;
import lombok.RequiredArgsConstructor;

@SpringBootApplication
@RequiredArgsConstructor
public class Application {

  private final Rollbar rollbar;

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @PostConstruct
  public void init() {
    rollbar.debug("CCS Scale CaT API service started...");
  }

}
