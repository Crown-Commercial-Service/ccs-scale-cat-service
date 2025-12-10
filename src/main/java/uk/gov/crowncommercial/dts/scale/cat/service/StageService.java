package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.util.Strings;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.StageException;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageType;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageTypesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.Stages;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageDataEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageTypesEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.StageDataRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.StageTypesRepo;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StageService {

  private final StageTypesRepo stageTypesRepo;
  private final StageDataRepo stageDataRepo;

  @Cacheable(value = "stageCache", key = "#root.methodName")
  public StageTypesRead getStageTypes() {
    final var stageTypes = stageTypesRepo.findAll();
    final var stageTypeRead = new StageTypesRead();

    if ((null != stageTypes) && !stageTypes.isEmpty()) {
        for (final StageTypesEntity entity : stageTypes) {
            stageTypeRead.addStageTypesItem(
                new StageType().id(entity.getId()).stageType(entity.getStageType()));
        }
    }

    return stageTypeRead;
  }

  public StagesRead getStagesForEventId(final String eventId) {
    if (Strings.isEmpty(eventId)) {
        log.error("getStagesForEventId - invalid eventId");
        throw new StageException("Cannot retrieve stage data, invalid eventId");
    }

    final var response = stageDataRepo.findByEventId(eventId);

    if (!response.isPresent()) {
        return new StagesRead()
                .eventId(eventId)
                .numberOfStages(0)
                .stages(null);
    }

    final var stagesRead = new StagesRead()
        .eventId(eventId)
        .numberOfStages(response.get().getStageIds().size());

    final List<Stages> listOfStages = new ArrayList<>();

    for (final Long thisStageId : response.get().getStageIds()) {
        listOfStages.add(new Stages().id(thisStageId));
    }

    stagesRead.stages(listOfStages);

    return stagesRead;
  }

  public boolean createOrUpdateStagesForEventId(final String eventId, final StagesWrite stagesWrite) {
    if (Strings.isEmpty(eventId) ||
        (null == stagesWrite) ||
        (null == stagesWrite.getStages()) ||
        stagesWrite.getStages().isEmpty()) {

        log.error("createOrUpdateStagesForEventId - invalid data for eventId: {}", eventId);
        throw new StageException("Cannot save stage data, invalid data for eventId: " + eventId);
    }

    final List<Long> listOfStageIds = new ArrayList<>();

    for (final Stages thisStage : stagesWrite.getStages()) {
        listOfStageIds.add(thisStage.getId());
    }

    try {
      stageDataRepo.save(
          StageDataEntity.builder()
              .eventId(eventId)
              .numberOfStages(listOfStageIds.size())
              .stageIds(listOfStageIds)
          .build());
      return true;
    } catch(final Exception e) {
        log.error("createOrUpdateStagesForEventId - error", e);
        throw new StageException("Unexpected error saving stages for eventId: " + eventId);
    }
  }
}
