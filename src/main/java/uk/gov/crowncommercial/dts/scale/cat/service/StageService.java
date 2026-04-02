package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.StageException;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageEventRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageEventWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageNameRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageNameWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageDataEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageEventEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageNameEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.StageDataRepo;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StageService {

  private final StageDataRepo stageDataRepo;

  public StagesRead getStagesForEventId(final String eventId) {
    if (Strings.isEmpty(eventId)) {
        log.error("getStagesForEventId - invalid eventId");
        throw new StageException("Cannot retrieve stage data, invalid eventId");
    }

    final Optional<StageDataEntity> response = stageDataRepo.findByEventId(eventId);

    if (!response.isPresent()) {
        return new StagesRead()
                .id(null)
                .eventId(eventId)
                .numberOfStages(0)
                .currentStage(0)
                .stageNames(List.of())
                .stageEvents(List.of());
    }

    List<StageNameRead> stageNames = new ArrayList<>();

    for (StageNameEntity entry : response.get().getStageNames()) {
        StageNameRead stageName = new StageNameRead();
        stageName.setId(entry.getId());
        stageName.setEventId(entry.getEventId());
        stageName.setStageNumber(entry.getStageNumber());
        stageName.setStageName(entry.getStageName());

        stageNames.add(stageName);
    }

    List<StageEventRead> eventsList = new ArrayList<>();

    for (StageEventEntity entry : response.get().getStageEvents()) {
        StageEventRead stageEvent = new StageEventRead();
        stageEvent.setId(entry.getId());
        stageEvent.setEventId(entry.getEventId());
        stageEvent.setStageNumber(entry.getStageNumber());
        stageEvent.setPriorEventId(entry.getPriorEventId());
        eventsList.add(stageEvent);
    }

    return new StagesRead()
        .id(response.get().getId())
        .eventId(response.get().getEventId())
        .numberOfStages(response.get().getNumberOfStages())
        .currentStage(response.get().getCurrentStage())
        .stageNames(stageNames)
        .stageEvents(eventsList);
  }

  public boolean createOrUpdateStagesForEventId(final String eventId, final StagesWrite stagesWrite) {
    if (Strings.isEmpty(eventId) || null == stagesWrite) {
        log.error("createOrUpdateStagesForEventId - invalid data for eventId: {}", eventId);
        throw new StageException("Cannot save stage data, invalid data for eventId: " + eventId);
    }

    try {
      List<StageNameEntity> stageNames = new ArrayList<>();

      for (StageNameWrite entry : stagesWrite.getStageNames()) {
          StageNameEntity stageNameEntity = new StageNameEntity();
          stageNameEntity.setId(entry.getId());
          stageNameEntity.setEventId(entry.getEventId());
          stageNameEntity.setStageNumber(entry.getStageNumber());
          stageNameEntity.setStageName(entry.getStageName());

          stageNames.add(stageNameEntity);
      }

      List<StageEventEntity> stageEvents = new ArrayList<>();

      for (StageEventWrite entry : stagesWrite.getStageEvents()) {
          StageEventEntity stageEventsEntity = new StageEventEntity();
          stageEventsEntity.setId(entry.getId());
          stageEventsEntity.setEventId(entry.getEventId());
          stageEventsEntity.setStageNumber(entry.getStageNumber());
          stageEventsEntity.setPriorEventId(entry.getPriorEventId());

          stageEvents.add(stageEventsEntity);
      }

      stageDataRepo.save(
          StageDataEntity.builder()
              .id(stagesWrite.getId())
              .eventId(stagesWrite.getEventId())
              .numberOfStages(stagesWrite.getNumberOfStages())
              .currentStage(stagesWrite.getCurrentStage())
              .stageNames(stageNames)
              .stageEvents(stageEvents)
          .build());

      return true;
    } catch(final Exception e) {
        log.error("createOrUpdateStagesForEventId - error", e);
        throw new StageException("Unexpected error saving stages for eventId: " + eventId);
    }
  }
}
