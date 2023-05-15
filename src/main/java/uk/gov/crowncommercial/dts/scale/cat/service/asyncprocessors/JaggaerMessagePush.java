package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.MessageAsync;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.MessageTaskStatus;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncConsumer;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.ErrorHandler;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.MessageService;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.input.MessageTaskData;

import java.util.List;
@RequiredArgsConstructor
@Component("JaggaerMessagePush")
@Slf4j
public class JaggaerMessagePush implements AsyncConsumer<MessageTaskData> {

    private final MessageService messageService;
    private final RetryableTendersDBDelegate dbDelegate;

    @Override
    public String getIdentifier(MessageTaskData data) {
        return null;
    }

    @Override
    public String accept(String principal, MessageTaskData data) {
        String messageId = null;
        var messageTask = dbDelegate.findMessageTaskById(data.getMessageId()).orElse(null);
        var event = dbDelegate.findProcurementEventById(messageTask.getEventId());
        messageTask.setStatus(MessageTaskStatus.INPROGRESS);
        updateMessages(messageTask);
        try {
             messageId = messageService.publishMessage(messageTask.getTimestamps().getCreatedBy(), event.get(), messageTask.getMessageRequest());
             messageTask.setStatus(MessageTaskStatus.COMPLETE);
            updateMessages(messageTask);
        }catch(JaggaerApplicationException e)
        {
            messageTask.setStatus(MessageTaskStatus.FAILED);
            updateMessages(messageTask);
        }
        return messageId;
    }

    private void updateMessages(MessageAsync messageTask) {
        messageTask.setTimestamps(Timestamps.updateTimestamps(messageTask.getTimestamps(),"AsyncExecutor"));
        dbDelegate.save(messageTask);
    }

    @Override
    public List<ErrorHandler> getErrorHandlers() {
        return null;
    }

    @Override
    public String getTaskName() {
        return "JaggaerMessagePush";
    }
}
