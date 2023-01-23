package uk.gov.crowncommercial.dts.scale.cat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import lombok.RequiredArgsConstructor;

//@SpringBootApplication
//@RequiredArgsConstructor
//@EnableScheduling
//@EnableAsync
//@EnableCaching
public class Application {

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

}
