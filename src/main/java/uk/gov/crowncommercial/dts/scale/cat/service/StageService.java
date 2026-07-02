package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.StageException;
import uk.gov.crowncommercial.dts.scale.cat.model.OCID;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageEventRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageEventWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageNameRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageNameWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementStageEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageDataEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageEventEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageNameEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.repo.StageDataRepo;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StageService {

  private final StageDataRepo stageDataRepo;
  private final ValidationService validationService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

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
                .currentStageNumber(1)
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
        .currentStageNumber(response.get().getCurrentStage())
        .stageNames(stageNames)
        .stageEvents(eventsList);
  }

  public boolean createOrUpdateStagesForEventId(final String eventId, final StagesWrite stagesWrite) {
    if (Strings.isEmpty(eventId) || null == stagesWrite) {
        log.error("createOrUpdateStagesForEventId - invalid data for eventId: {}", eventId);
        throw new StageException("Cannot save stage data, invalid data for eventId: " + eventId);
    }

    try {
        List<StageNameEntity> stageNames = Optional.ofNullable(stagesWrite.getStageNames())
                .orElseGet(Collections::emptyList)
                .stream()
                .map(this::mapToStageNameEntity)
                .toList();

        List<StageEventWrite> eventWrites = Optional.ofNullable(stagesWrite.getStageEvents())
                .orElseGet(Collections::emptyList);

        List<StageEventEntity> stageEvents = eventWrites.stream()
                .map(this::mapToStageEventEntity)
                .toList();

        if (!stageNames.isEmpty()) {
            if (eventWrites.isEmpty()) {
                // Handles the edge case
                stageNames.forEach(stage -> {
                    StageEventWrite entry = new StageEventWrite();
                    entry.id(stage.getId());
                    entry.eventId(stage.getEventId());
                    entry.stageNumber(stage.getStageNumber());
                    ensureStageNameIsStoredInProcurementStageEvent(stageNames, entry);
                });

            } else {
                // Standard flow
                eventWrites.forEach(entry -> ensureStageNameIsStoredInProcurementStageEvent(stageNames, entry));
            }
        }

      stageDataRepo.save(
          StageDataEntity.builder()
              .id(stagesWrite.getId())
              .eventId(stagesWrite.getEventId())
              .numberOfStages(stagesWrite.getNumberOfStages())
              .currentStage(stagesWrite.getCurrentStageNumber())
              .stageNames(stageNames)
              .stageEvents(stageEvents)
          .build());

      return true;
    } catch(final Exception e) {
        log.error("createOrUpdateStagesForEventId - error", e);
        throw new StageException("Unexpected error saving stages for eventId: " + eventId);
    }
  }

  private void ensureStageNameIsStoredInProcurementStageEvent(List<StageNameEntity> stageNames, StageEventWrite entry)
  {
      OCID eventOCID = validationService.validateEventId(entry.getEventId());

      Optional<ProcurementStageEvent> procurementEventForStage = retryableTendersDBDelegate
              .findProcurementStageEventByIdAndStageNumberAndOcdsAuthorityNameAndOcidPrefix(
                  Integer.valueOf(eventOCID.getInternalId()), entry.getStageNumber(), eventOCID.getAuthority(), eventOCID.getPublisherPrefix());

      if (procurementEventForStage.isPresent()) {
          for (StageNameEntity stageNameEntity : stageNames) {
              if (stageNameEntity.getStageNumber().equals(entry.getStageNumber())) {

                  if (null != stageNameEntity.getStageName() && !stageNameEntity.getStageName().isBlank()) {
                      procurementEventForStage.get().setStageDescription(stageNameEntity.getStageName());

                      retryableTendersDBDelegate.save(procurementEventForStage.get());
                  }

                  break;
              }
          }
      }
  }

    private StageNameEntity mapToStageNameEntity(StageNameWrite entry) {
        StageNameEntity entity = new StageNameEntity();
        entity.setId(entry.getId());
        entity.setEventId(entry.getEventId());
        entity.setStageNumber(entry.getStageNumber());
        entity.setStageName(entry.getStageName());
        return entity;
    }

    private StageEventEntity mapToStageEventEntity(StageEventWrite entry) {
        StageEventEntity entity = new StageEventEntity();
        entity.setId(entry.getId());
        entity.setEventId(entry.getEventId());
        entity.setStageNumber(entry.getStageNumber());
        entity.setPriorEventId(entry.getPriorEventId());
        return entity;
    }
}
