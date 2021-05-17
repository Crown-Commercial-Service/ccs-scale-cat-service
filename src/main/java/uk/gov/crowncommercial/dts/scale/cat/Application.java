package uk.gov.crowncommercial.dts.scale.cat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import lombok.RequiredArgsConstructor;

@SpringBootApplication
@EnableRetry
@RequiredArgsConstructor
public class Application {

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

}
