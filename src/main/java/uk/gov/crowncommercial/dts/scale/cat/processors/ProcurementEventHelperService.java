package uk.gov.crowncommercial.dts.scale.cat.processors;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;

import java.util.Optional;

public interface ProcurementEventHelperService {
    Optional<ProcurementEvent> getParentEvent(ProcurementEvent event, Integer parentTemplate);

    boolean isValidforUpdate(Requirement requirement);
}
