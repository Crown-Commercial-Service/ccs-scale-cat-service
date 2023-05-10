package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.*;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.input.JaggaerPublishEventData;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer.DocumentPushTask;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer.EventPublishTask;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer.PublishPrevalidationTask;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer.SupplierPushTask;

import java.util.Arrays;
import java.util.List;

@Component("JaggaerEventPublish")
public class JaggaerEventPublish extends AsyncMultiConsumer<JaggaerPublishEventData> {
    public JaggaerEventPublish(PublishPrevalidationTask prevalidationTask,
                               DocumentPushTask documentPushConsumer, SupplierPushTask supplierPushConsumer,
                               EventPublishTask eventPublisher){
        addConsumers("publishPreValidate",prevalidationTask);
        addConsumers("publishDocumentPush", documentPushConsumer);
        addConsumers("publishSupplierRefresh", supplierPushConsumer);
        addConsumers("publishEvent", eventPublisher);
    }

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
}
