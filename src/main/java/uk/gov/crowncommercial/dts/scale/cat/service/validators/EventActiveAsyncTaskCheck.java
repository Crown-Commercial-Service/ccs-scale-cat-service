package uk.gov.crowncommercial.dts.scale.cat.service.validators;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.VirusCheckStatus;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class EventActiveAsyncTaskCheck implements Validator<ProcurementEvent> {
    @Override
    public boolean test(ProcurementEvent event) {
        String status = event.getAsyncPublishedStatus();
        return null == status
                || "COMPLETED".equalsIgnoreCase(status)
            || "FAILED".equalsIgnoreCase(status);
    }

    @Override
    public ErrorDetails getErrorMessage(ProcurementEvent data) {
        return new ErrorDetails("AS001", "Another publish task already in progress");
    }
}
