package uk.gov.crowncommercial.dts.scale.cat.processors.inheritance;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;
import uk.gov.crowncommercial.dts.scale.cat.processors.DataTemplateProcessor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class TemplateProcessor implements DataTemplateProcessor {

    InheritanceProcessor<TemplateCriteria> asisProcessor = new TemplateCriteriaAsIsProcessor();
    InheritanceProcessor<TemplateCriteria> partProcessor = new TemplateCriteriaPartProcessor();
    InheritanceProcessor<TemplateCriteria> editProcessor = new TemplateCriteriaEditProcessor();
    InheritanceProcessor<TemplateCriteria> noneProcessor = new TemplateCriteriaNoneProcessor();

    @Override
    public DataTemplate process(DataTemplate template, DataTemplate oldData) {
        for (TemplateCriteria criteria : template.getCriteria()) {
            DataTemplateInheritanceType inheritance = criteria.getInheritanceNonOCDS();
            if (null != inheritance) {
                switch (inheritance) {
                    case AS_IS -> asisProcessor.accept(criteria, getQuestions(oldData));
                    case PART -> partProcessor.accept(criteria, getQuestions(oldData));
                    case EDIT -> editProcessor.accept(criteria, getQuestions(oldData));
                    case NONE -> noneProcessor.accept(criteria, Collections.emptyMap());
                }
            } else {
                noneProcessor.accept(criteria, Collections.emptyMap());
            }
        }

        return template;
    }

    private Map<String, Requirement> getQuestions(DataTemplate oldData) {
        Map<String, Requirement> result = new HashMap<>();

        for (TemplateCriteria criteria : oldData.getCriteria()) {
            if (null != criteria.getRequirementGroups())
                for (RequirementGroup reqGroups : criteria.getRequirementGroups()) {
                    if (null != reqGroups.getOcds() && null != reqGroups.getOcds().getRequirements())
                        for (Requirement req : reqGroups.getOcds().getRequirements()) {
                            if(null != req.getOcds())
                                result.put(reqGroups.getOcds().getId() + ":" + req.getOcds().getId(), req);
                        }
                }
        }

        return result;
    }
}
