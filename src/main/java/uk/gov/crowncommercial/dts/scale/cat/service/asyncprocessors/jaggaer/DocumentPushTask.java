package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.jaggaer;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.ErrorHandler;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.TaskConsumer;
import uk.gov.crowncommercial.dts.scale.cat.service.DocGenService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.ErrorHandlers;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.input.JaggaerPublishEventData;

import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class DocumentPushTask implements TaskConsumer<JaggaerPublishEventData> {
    private final ProcurementEventService eventService;
    private final DocGenService docGenService;

    @Override
    public String accept(String principal, JaggaerPublishEventData data) {
        docGenService.generateAndUploadDocuments(data.getProcId(), data.getEventId());
        eventService.uploadDocument(data.getProcId(), data.getEventId(), principal);
        return "document push done";
    }

    @Override
    public List<ErrorHandler> getErrorHandlers() {
        return ErrorHandlers.jaggaerHandlers;
    }

    @Override
    public String getTaskName() {
        return "DocumentPush";
    }
}
