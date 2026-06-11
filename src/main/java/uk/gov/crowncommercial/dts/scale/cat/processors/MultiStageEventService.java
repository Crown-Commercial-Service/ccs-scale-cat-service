package uk.gov.crowncommercial.dts.scale.cat.processors;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.CLOSED_STATUS;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.MULTI_STAGE_EVENT_TYPES;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.CreateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class MultiStageEventService {
    public static boolean isMultiStageEvent(CreateEvent event, ProcurementEvent existingEvent) {
        if (null == event.getNonOCDS() || null == event.getNonOCDS().getEventType()) {
            return false;
        }

        final boolean isMultiStage = MULTI_STAGE_EVENT_TYPES.contains(event.getNonOCDS().getEventType().getValue());

        if (isMultiStage) {
            return true;
        }

        if (null == event.getNonOCDS().getTemplateGroupId()) {
            return false;
        }

        if (null == existingEvent.getTemplateId() || null == existingEvent.getEventType()) {
            return false;
        }

        return event.getNonOCDS().getEventType().getValue().equals(existingEvent.getEventType());
    }

    public static List<Supplier> getSuppliers(RetryableTendersDBDelegate retryableTendersDBDelegate, JaggaerService jaggaerService, ProcurementEvent existingEvent) {
        var existingRfx = jaggaerService.getRfxWithSuppliers(existingEvent.getExternalEventId());

        if (null == existingRfx.getSuppliersList().getSupplier()) {
            return null;
        }

        var supplierOrgIds = existingRfx.getSuppliersList().getSupplier().stream()
                .map(s -> s.getCompanyData().getId())
                .collect(Collectors.toSet());

        return supplierOrgIds.stream().map(orgId -> {
                return Supplier.builder()
                    .companyData(CompanyData.builder().id(orgId).build())
                .build();
            }).collect(Collectors.toList());
    }

    public static void markComplete(RetryableTendersDBDelegate retryableTendersDBDelegate, ProcurementEvent existingEvent){
        // TODO - this method just closes in the tender db only.   Need to analyze and implement what needs to be done for Jaggaer event.
        existingEvent.setCloseDate(Instant.now());
        existingEvent.setTenderStatus(CLOSED_STATUS);
        retryableTendersDBDelegate.save(existingEvent);
    }
}