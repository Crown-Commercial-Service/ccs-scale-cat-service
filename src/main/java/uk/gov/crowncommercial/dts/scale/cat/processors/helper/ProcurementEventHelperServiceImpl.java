package uk.gov.crowncommercial.dts.scale.cat.processors.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;
import uk.gov.crowncommercial.dts.scale.cat.processors.ProcurementEventHelperService;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.CLOSED_STATUS_LIST;

@Component
@RequiredArgsConstructor
public class ProcurementEventHelperServiceImpl implements ProcurementEventHelperService {
    private final RetryableTendersDBDelegate retryableTendersDBDelegate;

    @Override
    public Optional<ProcurementEvent> getParentEvent(ProcurementEvent event, Integer parentTemplate){
        ProcurementProject project = event.getProject();
        Set<ProcurementEvent> projectEvents = retryableTendersDBDelegate.findProcurementEventsByProjectId(project.getId());
        return projectEvents.stream().sorted(Comparator.comparing(ProcurementEvent::getCreatedAt).reversed()).
                filter(t-> filterByTemplateId(t, event.getEventType(), parentTemplate)).findFirst();
    }

    @Override
    public void checkValidforUpdate(Requirement requirement) {
        if(null == requirement.getNonOCDS())
            return ;

        DataTemplateInheritanceType inheritanceType = requirement.getNonOCDS().getInheritance();
        if(inheritanceType == DataTemplateInheritanceType.ASIS){
            throw new IllegalArgumentException("AsIs requirement should not be modified");
        }
    }

    private boolean filterByTemplateId(ProcurementEvent event,String eventType, Integer parentTemplate) {
//        if(!isClosedStatus(event.getTenderStatus())) {
            if (null != event.getTemplateId()) {
                if (event.getTemplateId().equals(parentTemplate)) {
                    return eventType.equals(event.getEventType());
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
