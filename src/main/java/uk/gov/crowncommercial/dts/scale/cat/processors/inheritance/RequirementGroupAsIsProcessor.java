package uk.gov.crowncommercial.dts.scale.cat.processors.inheritance;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;


import java.util.Map;

import static uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType.AS_IS;

public class RequirementGroupAsIsProcessor implements InheritanceProcessor<RequirementGroup> {
    @Override
    public void accept(RequirementGroup requirementGroup, Map<String, Requirement> questions) {
        processRequirments(requirementGroup, questions,AS_IS);
    }

}
