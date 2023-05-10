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

@Component
@RequiredArgsConstructor
public class PublishPrevalidationTask implements TaskConsumer<JaggaerPublishEventData> {
    private final ProcurementEventService eventService;

    @SneakyThrows
    @Override
    public String accept(String principal, JaggaerPublishEventData data) {
        eventService.preValidatePublish(data.getProcId(), data.getEventId(), data.getPublishDates(), principal);
        return "Event validated";
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
