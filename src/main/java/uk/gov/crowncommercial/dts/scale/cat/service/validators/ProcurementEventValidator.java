package uk.gov.crowncommercial.dts.scale.cat.service.validators;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class ProcurementEventValidator {
    private final EventActiveAsyncTaskCheck asyncTaskCheck;
    private final EventDocumentVirusCheck virusCheck;
    public void checkPreConditions(ProcurementEvent data){
        MultiValidator.of(Arrays.asList(asyncTaskCheck, virusCheck)).test(data);
    }
}
