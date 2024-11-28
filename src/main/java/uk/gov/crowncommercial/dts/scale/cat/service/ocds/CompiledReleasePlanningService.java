package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

import java.math.BigDecimal;

/**
 * Populates release planning related information regarding a given project
 */
@Service
@Slf4j
public class CompiledReleasePlanningService {
    /**
     * Populate general planning related information regarding the project
     */
    public MapperResponse populateGeneral(Record1 re) {
        Planning1 planning = OcdsHelper.getPlanning(re);

        if (planning != null) {
            planning.setRationale(Constants.MAPPERS_PLANNING_RATIONALE);
        }

        return new MapperResponse(re);
    }

    /**
     * Populate budget planning related information regarding the project
     */
    public MapperResponse populateBudget(Record1 re, ProjectQuery pq) {
        Planning1 planning = OcdsHelper.getPlanning(re);

        if (planning != null && pq != null && pq.getProject() != null) {
            planning.setBudget(getBudget(pq.getProject()));
        }

        return new MapperResponse(re);
    }

    /**
     * Functionality to populate budget related information from OCDS question and answer data
     */
    private Budget1 getBudget(ProcurementProject pp) {
        if (pp != null) {
            ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);

            if (pe != null && pe.getProcurementTemplatePayload() != null && pe.getProcurementTemplatePayload().getCriteria() != null) {
                String maxBudget = EventsHelper.getData(Constants.MAPPERS_PLANNING_BUDGET_CRITERIA, Constants.MAPPERS_PLANNING_BUDGET_GROUP, Constants.MAPPERS_PLANNING_BUDGET_QUESTION, pe.getProcurementTemplatePayload().getCriteria());

                if (maxBudget != null && !maxBudget.trim().isEmpty()) {
                    // We now have a string representation of the budget data, however the output model wants a number.  So try and convert it, and fail silently if the data is unsuitable
                    try {
                        BigDecimal bd = BigDecimal.valueOf(Double.parseDouble(maxBudget));
                        Budget1 result = new Budget1();
                        Value1 amount = new Value1();
                        amount.amount(bd).currency(Currency1.GBP);
                        result.setAmount(amount);

                        return result;
                    } catch (Exception ex) {
                        log.warn("Error parsing project budget to numeric", ex);
                    }
                }
            }
        }

        return null;
    }
}