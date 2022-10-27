package uk.gov.crowncommercial.dts.scale.cat.processors.inheritance;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;

import java.util.Map;

public interface InheritanceProcessor <T>{
    void accept(T t,Map<String, Requirement> questions);


    default Requirement getQuestion(RequirementGroup requirementGroup, Requirement req, Map<String, Requirement> questions) {
        if(null != req.getNonOCDS().getInheritsFrom()) {
            String key = requirementGroup.getOcds().getId() + ":" + req.getNonOCDS().getInheritsFrom();
            return questions.get(key);
        }
        return null;
    }
}
