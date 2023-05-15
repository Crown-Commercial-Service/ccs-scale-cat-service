package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.MessageTaskStatus;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncConsumer;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.ErrorHandler;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.EventService;
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
        try {
             messageId = messageService.createorReplyMessage(messageTask.getTimestamps().getCreatedBy(), event.get(), messageTask.getMessageRequest());
            messageTask.setStatus(MessageTaskStatus.COMPLETE);
            messageTask.setTimestamps(Timestamps.updateTimestamps(messageTask.getTimestamps(),"AsyncExecutor"));
            dbDelegate.save(messageTask);
        }catch(Exception e)
        {
            messageTask.setStatus(MessageTaskStatus.FAILED);
            dbDelegate.save(messageTask);
        }
        return messageId;
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
