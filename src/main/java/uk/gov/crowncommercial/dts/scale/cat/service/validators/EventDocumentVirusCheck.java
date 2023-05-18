package uk.gov.crowncommercial.dts.scale.cat.service.validators;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.VirusCheckStatus;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class EventDocumentVirusCheck implements Validator<ProcurementEvent> {
    @Override
    public boolean test(ProcurementEvent event) {
        List<DocumentUpload> unsafeUploads = event.getDocumentUploads().stream()
                .filter((d) -> d.getExternalStatus() != VirusCheckStatus.SAFE)
                .collect(Collectors.toList());

        return 0 == unsafeUploads.size();
    }

    @Override
    public ErrorDetails getErrorMessage(ProcurementEvent data) {
        return new ErrorDetails("VS001", "Virus check failed for uploaded documents");
    }
}
