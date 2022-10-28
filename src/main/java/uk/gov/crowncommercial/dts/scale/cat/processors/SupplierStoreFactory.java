package uk.gov.crowncommercial.dts.scale.cat.processors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.processors.store.DOS6SupplierStore;
import uk.gov.crowncommercial.dts.scale.cat.processors.store.DatabaseSupplierStore;
import uk.gov.crowncommercial.dts.scale.cat.processors.store.JaggaerSupplierStore;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SupplierStoreFactory {

    private List<String> SPLIT_AGREEMENTS = Arrays.asList("RM1043.8");

    private List<String> SPLIT_EVENTS = Arrays.asList("FC");

    private final JaggaerSupplierStore jaggaerSupplierStore;
    private final DatabaseSupplierStore databaseSupplierStore;
    private final DOS6SupplierStore dos6SupplierStore;

    public SupplierStore getStore(ProcurementEvent event) {
        if (event.isTendersDBOnly()) {
            return databaseSupplierStore;
        } else {
            if(SPLIT_EVENTS.contains(event.getEventType())) {
                ProcurementProject project = event.getProject();
                if (null != project) {
                    String agreementNumber = project.getCaNumber().toUpperCase();
                    if (null != agreementNumber && SPLIT_AGREEMENTS.contains(agreementNumber)) {
                        return dos6SupplierStore;
                    }
                }
            }
            return jaggaerSupplierStore;
        }
    }
}
