package uk.gov.crowncommercial.dts.scale.cat;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import lombok.RequiredArgsConstructor;

import java.util.TimeZone;

@SpringBootApplication(exclude = {ElasticsearchDataAutoConfiguration.class})
@RequiredArgsConstructor
@EnableScheduling
@EnableAsync
@EnableCaching
@EnableFeignClients
public class Application {
  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

  // Forces server JVM to ignore its local operating system time
  @PostConstruct
  public void init() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }

}