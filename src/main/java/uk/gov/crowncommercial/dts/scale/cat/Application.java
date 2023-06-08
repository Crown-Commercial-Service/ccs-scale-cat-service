package uk.gov.crowncommercial.dts.scale.cat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRestClientAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import lombok.RequiredArgsConstructor;

@SpringBootApplication(exclude = {ReactiveElasticsearchRestClientAutoConfiguration.class,
ReactiveElasticsearchRepositoriesAutoConfiguration.class
})
@RequiredArgsConstructor
//@EnableScheduling
@EnableAsync
@EnableCaching
@EnableJpaRepositories("uk.gov.crowncommercial.dts.scale.cat.repo")
public class Application {

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

}
