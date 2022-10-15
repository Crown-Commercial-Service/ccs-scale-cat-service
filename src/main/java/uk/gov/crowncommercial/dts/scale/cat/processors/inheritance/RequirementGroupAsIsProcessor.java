package uk.gov.crowncommercial.dts.scale.cat.processors.inheritance;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;


import java.util.Map;

import static uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType.ASIS;

public class RequirementGroupAsIsProcessor implements InheritanceProcessor<RequirementGroup> {
    @Override
    public void accept(RequirementGroup requirementGroup, Map<String, Requirement> questions) {
        for(Requirement req : requirementGroup.getOcds().getRequirements()) {
            Requirement question = getQuestion(requirementGroup, req, questions);
            if(null != question) {
                req.getNonOCDS().setInheritance(ASIS);
                if(null != question.getNonOCDS().getOptions())
                    req.getNonOCDS().updateOptions(question.getNonOCDS().getOptions());
            }else{
                req.getNonOCDS().setInheritance(null);
            }
        }
    }
}