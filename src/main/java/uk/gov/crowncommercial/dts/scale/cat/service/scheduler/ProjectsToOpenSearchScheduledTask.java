package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectsToOpenSearchScheduledTask {

  @Scheduled(cron = "${config.external.s3.oppertunities.schedule}")
  @Transactional
  public void generateCSV() {
    log.info("Started projects data to open search scheduler process");
  }
  
}
