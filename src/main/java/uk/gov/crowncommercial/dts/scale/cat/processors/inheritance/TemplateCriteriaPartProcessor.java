package uk.gov.crowncommercial.dts.scale.cat.processors.inheritance;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;

import java.util.Map;

public class TemplateCriteriaPartProcessor implements InheritanceProcessor<TemplateCriteria> {
    InheritanceProcessor<RequirementGroup> asisProcessor = new RequirementGroupAsIsProcessor();
    InheritanceProcessor<RequirementGroup> partProcessor = new RequirementGroupPartProcessor();
    InheritanceProcessor<RequirementGroup> editProcessor = new RequirementGroupEditProcessor();
    InheritanceProcessor<RequirementGroup> noneProcessor = new RequirementGroupNoneProcessor();

    @Override
    public void accept(TemplateCriteria templateCriteria, Map<String, Requirement> questions) {
        for(RequirementGroup grp : templateCriteria.getRequirementGroups()) {
            DataTemplateInheritanceType inheritanceType = null;
            if(null != grp.getNonOCDS())
                 inheritanceType = grp.getNonOCDS().getInheritance();

            if(null == inheritanceType)
                inheritanceType = DataTemplateInheritanceType.NONE;

            switch (inheritanceType) {
                case PART -> partProcessor.accept(grp, questions);
                case ASIS -> asisProcessor.accept(grp, questions);
                case EDIT -> editProcessor.accept(grp, questions);
                case NONE -> noneProcessor.accept(grp, questions);
            }
        }
    }
}
