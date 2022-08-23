package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.OperationNotSupportedException;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.InvalidateEventRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.OwnerUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import javax.transaction.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.*;
import static uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils.evaluateDashboardStatusFromRfxSettingStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventTransitionService {

  private final UserProfileService userProfileService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  private final JaggaerService jaggaerService;

  private final ValidationService validationService;

  @Transactional
  public void completeExistingEvent(ProcurementEvent existingEvent, final String principal) {

    var rfxResponse = getSingleRfx(existingEvent.getExternalEventId());
    DashboardStatus dashboardStatus;

    if (FC_DA_NON_COMPLETE_EVENT_TYPES.contains(
        ViewEventType.fromValue(existingEvent.getEventType()))) {
      throw new OperationNotSupportedException(
          String.format(
              "You can not complete the event '%s' , you can only terminate the event",
              existingEvent.getEventType()));
    } else if (COMPLETE_EVENT_TYPES.contains(
        ViewEventType.fromValue(existingEvent.getEventType()))) {

      dashboardStatus = getDashboardStatus(existingEvent, rfxResponse);

      if (DashboardStatus.EVALUATING.equals(dashboardStatus)
          || DashboardStatus.TO_BE_EVALUATED.equals(dashboardStatus)) { // check with Mark
        updateDbEvent(existingEvent, principal, COMPLETE_STATUS);

      } else {
        throw new OperationNotSupportedException(
            String.format(
                "You can not complete the event '%s' as its not eligible to be completed, Status : '%s' ",
                existingEvent.getEventID(), dashboardStatus));
      }
    } else {
      throw new OperationNotSupportedException(
          String.format(
              "You can not complete the event '%s' , you can only terminate the event",
              existingEvent.getEventType()));
    }
  }

  /**
   * Terminate Event in Jaggaer
   *
   * @param procId
   * @param eventId
   * @param type
   * @param openCompletedEvent
   */
  @Transactional
  public void terminateEvent(
      final Integer procId,
      final String eventId,
      final TerminationType type,
      final String principal,
      boolean openCompletedEvent) {
    var user =
        userProfileService
            .resolveBuyerUserProfile(principal)
            .orElseThrow(() -> new AuthorisationFailureException(ERR_MSG_JAGGAER_USER_NOT_FOUND))
            .getUserId();
    var terminatingEvent = validationService.validateProjectAndEventIds(procId, eventId);

    if (terminatingEvent.isTendersDBOnly()) {
      updateDbEvent(terminatingEvent, principal, type.name());
    } else {
        final var invalidateEventRequest =
            InvalidateEventRequest.builder()
                .invalidateReason(type.getValue())
                .rfxId(terminatingEvent.getExternalEventId())
                .rfxReferenceCode(terminatingEvent.getExternalReferenceId())
                .operatorUser(OwnerUser.builder().id(user).build())
                .build();
        log.info("Invalidate event request: {}", invalidateEventRequest);
        jaggaerService.invalidateEvent(invalidateEventRequest);
        // update status
        updateStatusAndDates(principal, terminatingEvent, type);
    }

    if(openCompletedEvent){
        openCompletedEvent(procId,terminatingEvent,principal);
    }
  }

    @Transactional
    public void openCompletedEvent(
            final Integer projectId,
            final ProcurementEvent terminatedEvent,
            final String principal){
        var procurementEvents = retryableTendersDBDelegate.findProcurementEventsByProjectId(projectId);

    Optional<ProcurementEvent> previousEventOptional=procurementEvents.stream()
        .filter(
            procurementEvent ->
                (COMPLETE_STATUS.equalsIgnoreCase(procurementEvent.getTenderStatus())))
        .sorted(Comparator.comparing(ProcurementEvent::getCloseDate)).findFirst();

        if(previousEventOptional.isPresent()){
            ProcurementEvent completedEvent=previousEventOptional.get();
            /*ViewEventType previousEventType=ViewEventType.fromValue(previousEventOptional.get().getEventType());
            ViewEventType terminatedEventType=ViewEventType.fromValue(terminatedEvent.getEventType());*/
            // extract Dashboard status
            completedEvent.setTenderStatus(TenderStatus.PLANNING.getValue());
        }
    }

  public void updateStatusAndDates(
      final String principal, final ProcurementEvent procurementEvent,TerminationType type) {

    var exportRfxResponse = getSingleRfx(procurementEvent.getExternalEventId());

    /*var tenderStatus = TenderStatus.CANCELLED.getValue();
    if (exportRfxResponse.getRfxSetting() != null) {
      var rfxStatus =
          jaggaerAPIConfig
              .getRfxStatusAndEventTypeToTenderStatus()
              .get(exportRfxResponse.getRfxSetting().getStatusCode());

      tenderStatus =
          rfxStatus != null && rfxStatus.get(procurementEvent.getEventType()) != null
              ? rfxStatus.get(procurementEvent.getEventType()).getValue()
              : null;
    }*/

    procurementEvent.setUpdatedAt(Instant.now());
    procurementEvent.setUpdatedBy(principal);
    if (exportRfxResponse.getRfxSetting().getPublishDate() != null) {
      procurementEvent.setPublishDate(
          exportRfxResponse.getRfxSetting().getPublishDate().toInstant());
    }
    if (exportRfxResponse.getRfxSetting().getCloseDate() != null) {
      procurementEvent.setCloseDate(exportRfxResponse.getRfxSetting().getCloseDate().toInstant());
    } else {
      procurementEvent.setCloseDate(Instant.now());
    }

    procurementEvent.setTenderStatus(type.getValue());

    retryableTendersDBDelegate.save(procurementEvent);
  }



  private ExportRfxResponse getSingleRfx(final String externalEventId) {
    return jaggaerService.searchRFx(Set.of(externalEventId)).stream()
        .findFirst()
        .orElseThrow(
            () -> new TendersDBDataException(format(ERR_MSG_RFX_NOT_FOUND, externalEventId)));
  }

  private DashboardStatus getDashboardStatus(
      ProcurementEvent existingEvent, ExportRfxResponse rfxResponse) {
    DashboardStatus dashboardStatus;
    if (Objects.nonNull(rfxResponse) && Objects.nonNull(rfxResponse.getRfxSetting())) {
      dashboardStatus = evaluateDashboardStatusFromRfxSettingStatus(rfxResponse.getRfxSetting());
    } else {
      throw new OperationNotSupportedException(
          String.format(
              "You can not complete the event Id : {} , event type : '%s' ",
              existingEvent.getEventID(), existingEvent.getEventType()));
    }
    return dashboardStatus;
  }

  private void updateDbEvent(ProcurementEvent existingEvent, String principal, String status) {
    existingEvent.setTenderStatus(status);
    existingEvent.setUpdatedAt(Instant.now());
    existingEvent.setUpdatedBy(principal);
    existingEvent.setCloseDate(Instant.now());
    retryableTendersDBDelegate.save(existingEvent);
  }
}
