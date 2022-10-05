package uk.gov.crowncommercial.dts.scale.cat.processors.helper;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;
import uk.gov.crowncommercial.dts.scale.cat.processors.ProcurementEventHelperService;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.CLOSED_STATUS_LIST;

@Component
public class ProcurementEventHelperServiceImpl implements ProcurementEventHelperService {
    @Override
    public Optional<ProcurementEvent> getParentEvent(ProcurementEvent event, Integer parentTemplate){
        ProcurementProject project = event.getProject();
        Set<ProcurementEvent> events = project.getProcurementEvents();
        return events.stream().sorted(Comparator.comparing(ProcurementEvent::getCreatedAt).reversed()).
                filter(t-> filterByTemplateId(t, event.getEventType(), parentTemplate)).findFirst();
    }

    @Override
    public boolean isValidforUpdate(Requirement requirement) {
        if(null == requirement.getNonOCDS())
            return true;

        DataTemplateInheritanceType inheritanceType = requirement.getNonOCDS().getInheritance();
        return null == inheritanceType || inheritanceType != DataTemplateInheritanceType.ASIS;
    }

    private boolean filterByTemplateId(ProcurementEvent t,String eventType, Integer parentTemplate) {
//        if(!isClosedStatus(t.getTenderStatus())) {
            if (null != t.getTemplateId()) {
                if (t.getTemplateId().equals(parentTemplate)) {
                    return eventType.equals(t.getEventType());
                }
            }
//        }
        return false;
    }

    private boolean isClosedStatus(String tenderStatus){
        if(null == tenderStatus)
            return false;
        return CLOSED_STATUS_LIST.contains(tenderStatus.toLowerCase());
    }
}
