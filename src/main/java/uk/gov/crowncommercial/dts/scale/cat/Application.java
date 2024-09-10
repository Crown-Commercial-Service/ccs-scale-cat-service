package uk.gov.crowncommercial.dts.scale.cat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import uk.gov.crowncommercial.dts.scale.cat.repo.search.SearchProjectRepo;

@SpringBootApplication(exclude = {ElasticsearchDataAutoConfiguration.class})
@RequiredArgsConstructor
@EnableScheduling
@EnableAsync
@EnableCaching
@EnableFeignClients
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
@EnableElasticsearchRepositories(basePackages= {"uk.gov.crowncommercial.dts.scale.cat.repo.search"})
@EnableJpaRepositories(basePackages = {"uk.gov.crowncommercial.dts.scale.cat.repo"}, excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {SearchProjectRepo.class})})
public class Application {
  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }
}