package uk.gov.crowncommercial.dts.scale.cat.processors.inheritance;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;

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

    default  void copyOptions(Requirement req, Requirement question) {
        if(null != question.getNonOCDS().getOptions())
            req.getNonOCDS().updateOptions(question.getNonOCDS().getOptions());
        if(null != question.getNonOCDS().getTimelineDependency())
            req.getNonOCDS().setTimelineDependency(question.getNonOCDS().getTimelineDependency());
    }

    default void processRequirments(RequirementGroup requirementGroup, Map<String, Requirement> questions, DataTemplateInheritanceType inheritance) {
        for(Requirement req : requirementGroup.getOcds().getRequirements()) {
            Requirement question = getQuestion(requirementGroup, req, questions);
            if(null != question) {
                req.getNonOCDS().setInheritance(inheritance);
                copyOptions(req, question);
            }else{
                req.getNonOCDS().setInheritance(null);
            }
        }
    }
<<<<<<< HEAD
=======

>>>>>>> release/int
}
