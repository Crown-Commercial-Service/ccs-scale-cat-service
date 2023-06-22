package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ValidationService;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.input.JaggaerPublishEventData;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer.DocumentPushTask;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer.EventPublishTask;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer.PublishPrevalidationTask;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer.SupplierPushTask;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;

@Component("JaggaerEventPublish")
@RequiredArgsConstructor
public class JaggaerEventPublish extends AsyncMultiConsumer<JaggaerPublishEventData> {

    private final RetryableTendersDBDelegate dbDelegate;
    private final ValidationService validationService;
    private final DocumentPushTask documentPushConsumer;
    private final SupplierPushTask supplierPushConsumer;
    private final EventPublishTask eventPublisher;

    @Override
    public String getIdentifier(JaggaerPublishEventData data) {
        return data.getProcId() + "/" + data.getEventId();
    }

    @Override
    public List<ErrorHandler> getErrorHandlers() {
        return Arrays.asList(JaggaerErrorHandler.INSTANCE, NoopErrorHandler.INSTANCE);
    }

    @Override
    public String getTaskName() {
        return "JaggaerEvent Publish";
    }

    @Override
    @Transactional
    public void onStatusChange(String principal, JaggaerPublishEventData data, AsyncTaskStatus taskStatus) {
        markStatus(data, getStatus(taskStatus));
    }

    private String getStatus(AsyncTaskStatus status){
        switch (status){
            case SCHEDULED:
                return "SCHEDULED";
            case RETRY:
            case IN_FLIGHT:
                return "IN_FLIGHT";
            case FAILED:
                return "FAILED";
            case COMPLETED:
                return "COMPLETED";
        }
        return "Unknown";
    }

    private void markStatus(JaggaerPublishEventData data, String status) {
        ProcurementEvent event = validationService.validateProjectAndEventIds(data.getProcId(), data.getEventId());
        event.setAsyncPublishedStatus(status);
        dbDelegate.save(event);
    }

    @Override
    protected List<TaskConsumer<JaggaerPublishEventData>> getConsumers() {
        return Arrays.asList(documentPushConsumer, supplierPushConsumer, eventPublisher);
    }
}
