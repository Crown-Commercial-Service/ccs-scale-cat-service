package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.ErrorHandler;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.TaskConsumer;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.ErrorHandlers;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.input.JaggaerPublishEventData;

import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class SupplierPushTask implements TaskConsumer<JaggaerPublishEventData> {
    private final ProcurementEventService eventService;
    @SneakyThrows
    @Override
    public String accept(String principal, JaggaerPublishEventData data) {
        int supplierCount = eventService.jaggaerSupplierRefresh(data.getProcId(), data.getEventId(), principal);
        if(supplierCount > 0){
            return "Refreshed " + supplierCount + " suppliers to Jaggaer";
        }
        return "No supplier details are refreshed";
    }

    @Override
    public List<ErrorHandler> getErrorHandlers() {
        return ErrorHandlers.jaggaerHandlers;
    }

    @Override
    public String getTaskName() {
        return "SupplierPush";
    }
}
