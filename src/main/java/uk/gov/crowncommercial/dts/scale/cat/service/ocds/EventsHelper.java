package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

import java.util.List;

public class EventsHelper {
    public static ProcurementEvent getFirstPublishedEvent(ProcurementProject pp){
        return pp.getProcurementEvents().stream().filter(s -> null != s.getPublishDate())
                .sorted((d, s) -> {return d.getPublishDate().compareTo(s.getPublishDate());})
                .findFirst().orElseGet(null);
    }

    public static ProcurementEvent getLastPublishedEvent(ProcurementProject pp){
        return pp.getProcurementEvents().stream().filter(s -> null != s.getPublishDate())
                .sorted((s, d) -> {return d.getPublishDate().compareTo(s.getPublishDate());})
                .findFirst().orElseGet(null);
    }

    public static ProcurementEvent getAwardEvent(ProcurementProject pp){
        return getLastPublishedEvent(pp);
    }

    public static String getData(String groupId, String groupDescription, String requirementId, List<TemplateCriteria> criteria) {
        for(TemplateCriteria tc : criteria){
            for(RequirementGroup rg : tc.getRequirementGroups()){
                if(rg.getOcds().getId().equalsIgnoreCase(groupId) && (null != rg.getOcds().getDescription()) && rg.getOcds().getDescription().equalsIgnoreCase(groupDescription)){
                    for(Requirement r : rg.getOcds().getRequirements()){
                        if(r.getOcds().getId().equalsIgnoreCase(requirementId) ){
                            return r.getNonOCDS().getOptions().stream().filter(t->t.getSelect()).findFirst(). map(t->null != t? t.getValue() : null).orElseGet(()-> null);
                        }
                    }
                }
            }
        }
        return null;
    }
}
