package uk.gov.crowncommercial.dts.scale.cat.processors.inheritance;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Question;

import java.util.Map;

public class TemplateCriteriaAsIsProcessor implements InheritanceProcessor<TemplateCriteria> {
    InheritanceProcessor<RequirementGroup> asisProcessor = new RequirementGroupAsIsProcessor();

    @Override
    public void accept(TemplateCriteria templateCriteria, Map<String, Requirement> questions) {
        for (RequirementGroup grp : templateCriteria.getRequirementGroups()) {
            if (null != grp.getNonOCDS()) {
                grp.getNonOCDS().setInheritance(DataTemplateInheritanceType.AS_IS);
                asisProcessor.accept(grp, questions);
            }
        }
    }
}
