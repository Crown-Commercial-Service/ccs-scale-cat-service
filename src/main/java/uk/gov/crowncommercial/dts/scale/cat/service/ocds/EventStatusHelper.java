package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TerminationType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.CLOSED_STATUS;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.COMPLETE_STATUS;

public class EventStatusHelper {
    private static final List<String> TERMINATION_LIST = List.of(TerminationType.CANCELLED.getValue(),
            TerminationType.WITHDRAWN.getValue(), TerminationType.UNSUCCESSFUL.getValue());
    private static final List<Integer> AWARD_STATUS_LIST = List.of(JaggaerStatus.AWARDED.getValue(),
            JaggaerStatus.AWARDED_OFFLINE.getValue(), JaggaerStatus.MIXED_AWARDING.getValue());

    private static final List<Integer> PLANNED_STATUS_LIST = List.of(JaggaerStatus.TOBE_PUBLISHED.getValue(),
            JaggaerStatus.TOBE_APPROVED.getValue(), JaggaerStatus.APPROVAL_DECLINED.getValue());

    private static final List<Integer> ACTIVE_STATUS_LIST = List.of(JaggaerStatus.RUNNING.getValue(),
            JaggaerStatus.QUALIFICATION_CLARIFICATION.getValue(), JaggaerStatus.AUCTION.getValue(),
            JaggaerStatus.BEST_FINAL_OFFER.getValue(), JaggaerStatus.TIOC_PROGRESS.getValue(),
            JaggaerStatus.TOBE_EVALUATED.getValue(), JaggaerStatus.QUALIFICATION_EVALUATION.getValue(),
            JaggaerStatus.TECHNICAL_EVALUATION.getValue(), JaggaerStatus.COMMERCIAL_EVALUATION.getValue(),
            JaggaerStatus.BEST_FINAL_OFFER_EVALUATION.getValue(), JaggaerStatus.TIOC_CLOSED.getValue()
            );

    private static final List<Integer> OPEN_STATUS_LIST = List.of(JaggaerStatus.RUNNING.getValue(),
            JaggaerStatus.QUALIFICATION_CLARIFICATION.getValue(), JaggaerStatus.AUCTION.getValue(),
            JaggaerStatus.BEST_FINAL_OFFER.getValue(), JaggaerStatus.TIOC_PROGRESS.getValue());

    public static TenderStatus getStatus(RfxSetting rfxSetting, ProcurementEvent pe) {
        if (null != pe.getTenderStatus()) {
            String status = pe.getTenderStatus().strip();
            TenderStatus result = getStatusFromTendersStatus(status);
            if (null != result)
                return result;
        }

        if (Objects.isNull(rfxSetting.getPublishDate()) || isPlanning(rfxSetting)) {
            return TenderStatus.PLANNING;
        } else if (Objects.nonNull(rfxSetting.getCloseDate())
                && rfxSetting.getCloseDate().isAfter(OffsetDateTime.now())) {
            return TenderStatus.ACTIVE;
        } else {
            return evaluateDashboardStatusFromRfxSettingStatus(rfxSetting);
        }
    }

    private static TenderStatus evaluateDashboardStatusFromRfxSettingStatus(RfxSetting rfxSetting) {
        if (isAwarded(rfxSetting)) {
            return TenderStatus.COMPLETE;
        }else if(isActive(rfxSetting)) {
            return TenderStatus.ACTIVE;
        }
        JaggaerStatus status = JaggaerStatus.fromValue(rfxSetting.getStatusCode());
        if(null != status){
            switch (status){
                case CLOSED:
                    return TenderStatus.UNSUCCESSFUL;
                case SUSPENDED:
                    return TenderStatus.WITHDRAWN;
                case ENDED:
                    return TenderStatus.CANCELLED;
            }
        }

        return TenderStatus.ACTIVE;
    }

    private static boolean hasValue(List<Integer> list, Integer value) {
        return list.stream()
                .anyMatch(value::equals);
    }

    private static TenderStatus getStatusFromTendersStatus(String status) {

        switch (status) {
            case COMPLETE_STATUS:
            case CLOSED_STATUS:
                return TenderStatus.COMPLETE;
        }

        if(TERMINATION_LIST.contains(status)) {
            TerminationType tt = TerminationType.fromValue(status);
            if (null != tt) {
                switch (tt) {
                    case CANCELLED:
                        return TenderStatus.CANCELLED;
                    case WITHDRAWN:
                        return TenderStatus.WITHDRAWN;
                    case UNSUCCESSFUL:
                        return TenderStatus.UNSUCCESSFUL;
                }
            }
        }
        return null;
    }

    public static boolean isAwarded(RfxSetting rfxSetting){
        return hasValue(AWARD_STATUS_LIST, rfxSetting.getStatusCode());
    }

    public static boolean isPlanning(RfxSetting rfxSetting){
        return hasValue(PLANNED_STATUS_LIST, rfxSetting.getStatusCode());
    }

    public static boolean isActive(RfxSetting rfxSetting){
        return hasValue(ACTIVE_STATUS_LIST, rfxSetting.getStatusCode());
    }

    public static boolean isOpen(RfxSetting rfxSetting){
        return hasValue(OPEN_STATUS_LIST, rfxSetting.getStatusCode());
    }
}
