package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

import java.util.List;
import java.util.Set;

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
            String result = getData(groupId, groupDescription, requirementId, tc.getRequirementGroups());
            if(null != result)
                return null;
        }
        return null;
    }

    private static String getData(String groupId, String groupDescription, String requirementId, Set<RequirementGroup> requirementGroups) {
        for(RequirementGroup rg : requirementGroups){
            if(isReqGroupMatch(rg, groupId, groupDescription)){
                for(Requirement r : rg.getOcds().getRequirements()){
                    if(isReqMatch(r, requirementId)){
                        return getFirstValue(r.getNonOCDS().getOptions());
                    }
                }
            }
        }
        return null;
    }

    private static String getFirstValue(List<Requirement.Option> options){
        return options.stream().filter(t->t.getSelect()).findFirst(). map(t->null != t? t.getValue() : null).orElseGet(()-> null);
    }

    private static boolean isReqMatch(Requirement r, String requirementId) {
        return r.getOcds().getId().equalsIgnoreCase(requirementId);
    }

    private static boolean isReqGroupMatch(RequirementGroup rg, String groupId, String groupDescription) {
        return rg.getOcds().getId().equalsIgnoreCase(groupId) && (null != rg.getOcds().getDescription()) && rg.getOcds().getDescription().equalsIgnoreCase(groupDescription);
    }
}
