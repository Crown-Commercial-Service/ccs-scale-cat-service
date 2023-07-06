package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectsToOpenSearchScheduledTask {

  @Transactional
  public void syncProjectsToOpenSearch() {
    log.info("Stated projects data to open search scheduler process");
  }
  
}
