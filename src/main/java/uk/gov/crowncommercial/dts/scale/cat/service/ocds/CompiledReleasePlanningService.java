package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

import java.math.BigDecimal;

@Service
@Slf4j
public class CompiledReleasePlanningService {

    public MapperResponse populateGeneral(Record1 re, ProjectQuery pq) {
        log.debug("populating General");
        Planning1 planning = OcdsHelper.getPlanning(re);
        planning.setRationale("rationale");
        return new MapperResponse(re);
    }

    public MapperResponse populateBudget(Record1 re, ProjectQuery pq) {
        log.debug("populating Budget");
        Planning1 planning = OcdsHelper.getPlanning(re);
        planning.setBudget(getBudget(pq.getProject()));
        return new MapperResponse(re);
    }

    private Budget1 getBudget(ProcurementProject pp) {
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        String maxBudget = EventsHelper.getData("Group 18", "Set your budget ",  "Question 2", pe.getProcurementTemplatePayload().getCriteria());
        if(null != maxBudget && maxBudget.trim().length() > 0){
            BigDecimal bd = BigDecimal.valueOf(Double.parseDouble(maxBudget));
            Budget1 result = new Budget1();
            Value1 amount = new Value1();
            amount.amount(bd).currency(Currency1.GBP);
            result.setAmount(amount);
            return result;
        }
        return null;
    }

    public MapperResponse populateDocuments(Record1 re, ProjectQuery pq) {
        log.warn("populating Documents not yet implemented");
        Planning1 planning = OcdsHelper.getPlanning(re);
        return new MapperResponse(re);
    }

    public MapperResponse populateMilestones(Record1 re, ProjectQuery pq) {
        log.warn("populating Milestones not yet implemented");
        Planning1 planning = OcdsHelper.getPlanning(re);
        return new MapperResponse(re);
    }
}
