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
import static uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils.getDashboardStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventTransitionService {

  public static final String CAN_NOT_COMPLETE_ERROR_MESSAGE =
      "You can not complete the event id : '%s' , event type : '%s' , you can only terminate the event";
  public static final String CAN_NOT_ELIGIBILE_ERROR_MESSAGE =
      "You can not complete the event id '%s' , eventType: '%s' as its not eligible to be completed, Status : '%s' ";
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

      throwExceptionWithMsg(CAN_NOT_COMPLETE_ERROR_MESSAGE, existingEvent, null);

    } else if (COMPLETE_EVENT_TYPES.contains(
        ViewEventType.fromValue(existingEvent.getEventType()))) {
      dashboardStatus = getDashboardStatusForRfx(existingEvent, rfxResponse);

      if (DashboardStatus.EVALUATING.equals(dashboardStatus)
          || DashboardStatus.TO_BE_EVALUATED.equals(dashboardStatus)) {
        updateDbEvent(existingEvent, principal, COMPLETE_STATUS);
      } else {
        throwExceptionWithMsg(CAN_NOT_ELIGIBILE_ERROR_MESSAGE, existingEvent, dashboardStatus);
      }

    } else if (ASSESMENT_COMPLETE_EVENT_TYPES.contains(
        ViewEventType.fromValue(existingEvent.getEventType()))) {

        dashboardStatus = getDashboardStatus( null!=rfxResponse?rfxResponse.getRfxSetting():null,existingEvent);

      if (DashboardStatus.ASSESSMENT.equals(dashboardStatus)) {
        updateDbEvent(existingEvent, principal, COMPLETE_STATUS);

      } else {
        throwExceptionWithMsg(CAN_NOT_ELIGIBILE_ERROR_MESSAGE, existingEvent, dashboardStatus);
      }
    } else {
      throwExceptionWithMsg(CAN_NOT_COMPLETE_ERROR_MESSAGE, existingEvent, null);
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

    if (openCompletedEvent) {
      openCompletedEvent(procId, terminatingEvent, principal);
    }
  }

  @Transactional
  public void openCompletedEvent(
      final Integer projectId, final ProcurementEvent terminatedEvent, final String principal) {
    var procurementEvents = retryableTendersDBDelegate.findProcurementEventsByProjectId(projectId);

    Optional<ProcurementEvent> previousEventOptional =
        procurementEvents.stream()
            .filter(
                procurementEvent ->
                    (COMPLETE_STATUS.equalsIgnoreCase(procurementEvent.getTenderStatus())))
            .sorted(Comparator.comparing(ProcurementEvent::getCloseDate))
            .findFirst();

    if (previousEventOptional.isPresent()) {
      ProcurementEvent completedEvent = previousEventOptional.get();
      completedEvent.setTenderStatus(TenderStatus.PLANNING.getValue());
    } else {
      Optional<ProcurementEvent> tbdEvent =
          procurementEvents.stream()
              .filter(
                  procurementEvent ->
                      (ViewEventType.fromValue(procurementEvent.getEventType())
                          .equals(ViewEventType.TBD)))
              .sorted(Comparator.comparing(ProcurementEvent::getCloseDate))
              .findFirst();
      if (tbdEvent.isPresent()) {
        tbdEvent.get().setTenderStatus(TenderStatus.PLANNING.getValue());
      }
    }
  }

  public void updateStatusAndDates(
      final String principal, final ProcurementEvent procurementEvent, TerminationType type) {

    var exportRfxResponse = getSingleRfx(procurementEvent.getExternalEventId());

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
    if(null == externalEventId)
      return null;
    return jaggaerService.searchRFx(Set.of(externalEventId)).stream()
        .findFirst()
        .orElseThrow(
            () -> new TendersDBDataException(format(ERR_MSG_RFX_NOT_FOUND, externalEventId)));
  }

  private DashboardStatus getDashboardStatusForRfx(
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

  private void throwExceptionWithMsg(
      String errorMessage, ProcurementEvent existingEvent, DashboardStatus dashboardStatus) {

    if (Objects.isNull(dashboardStatus)) {
      throw new OperationNotSupportedException(
          String.format(
              CAN_NOT_COMPLETE_ERROR_MESSAGE,
              existingEvent.getEventID(),
              existingEvent.getEventType()));
    } else {
      throw new OperationNotSupportedException(
          String.format(
              CAN_NOT_ELIGIBILE_ERROR_MESSAGE,
              existingEvent.getEventID(),
              existingEvent.getEventType(),
              dashboardStatus));
    }
  }

}
