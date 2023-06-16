package uk.gov.crowncommercial.dts.scale.cat.processors.inheritance;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;

import java.util.Map;

import static uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType.ASIS;

public class RequirementGroupPartProcessor implements InheritanceProcessor<RequirementGroup> {
    @Override
    public void accept(RequirementGroup requirementGroup, Map<String, Requirement> questions) {
        for(Requirement req : requirementGroup.getOcds().getRequirements()) {
            DataTemplateInheritanceType inheritanceType = null;
            if(null != req.getNonOCDS())
                inheritanceType = req.getNonOCDS().getInheritance();

            if(null == inheritanceType)
                continue;

            switch (inheritanceType) {
                case PART, NONE -> req.getNonOCDS().setInheritance(null);
                case ASIS, EDIT -> {
                    Requirement question = getQuestion(requirementGroup, req, questions);
                    if(null != question) {
                        req.getNonOCDS().setInheritance(inheritanceType);
                        copyOptions(req, question);
                    }else{
                        req.getNonOCDS().setInheritance(null);
                    }
                }
            }
        }
    }
}
