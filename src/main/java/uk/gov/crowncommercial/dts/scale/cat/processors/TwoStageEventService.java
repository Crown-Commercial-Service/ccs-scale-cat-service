package uk.gov.crowncommercial.dts.scale.cat.processors;

import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.CreateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class TwoStageEventService {
    public boolean isTwoStageEvent(CreateEvent event, ProcurementEvent existingEvent) {
        if (null != event.getNonOCDS() && null != event.getNonOCDS().getEventType() && null != event.getNonOCDS().getTemplateGroupId()) {
            if (existingEvent.getTemplateId() != null) {
                return event.getNonOCDS().getEventType().getValue().equals(existingEvent.getEventType());
            }
        }
        return false;
    }

    public List<Supplier> getSuppliers(RetryableTendersDBDelegate retryableTendersDBDelegate, JaggaerService jaggaerService, ProcurementEvent existingEvent) {
        var existingRfx = jaggaerService.getRfxWithSuppliers(existingEvent.getExternalEventId());

        if (existingRfx.getSuppliersList().getSupplier() != null) {
            var supplierOrgIds = existingRfx.getSuppliersList().getSupplier().stream().map(s -> s.getCompanyData().getId()).collect(Collectors.toSet());
            return supplierOrgIds.stream().map(orgId -> {
                        var companyData = CompanyData.builder().id(orgId).build();
                        return Supplier.builder().companyData(companyData).build();
                    }).collect(Collectors.toList());
        }
        return null;
    }

    public void markComplete(RetryableTendersDBDelegate retryableTendersDBDelegate, ProcurementEvent existingEvent){
        // TODO - this method just closes in the tender db only.   Need to analyze and implement what needs to be done for Jaggaer event.
        existingEvent.setCloseDate(Instant.now());
        existingEvent.setTenderStatus(Constants.CLOSED_STATUS);
        retryableTendersDBDelegate.save(existingEvent);
    }
}