package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.assessment.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentToolDimension;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import java.util.*;

@Component
public class SpringAssessmentToolFactory implements ApplicationContextAware, AssessmentToolFactory {

    private final List<String> exclusionPolicy = Arrays.asList("EXCL_NoopPolicy", "EXCL_AllReqNonZero", "EXCL_AtleastOneNonZero");
    private final List<String> dimensionCalculator = Arrays.asList("DIM_NoopCalculator", "DIM_StandardWeighted", "DIM_Pricing");
    private final List<String> assessmentCalculator = Arrays.asList("ASMT_StandardWeighted");

    private ApplicationContext context;

    public AssessmentToolCalculator getAssessmentTool(AssessmentEntity assessment){
        AssessmentTool tool = assessment.getTool();

        List<AssessmentToolDimension> toolDimensionList =  tool.getDimensionMapping();

        Map<String, DimensionScoreCalculator> dimCalcList = new HashMap<>();
        Map<String, ExclusionPolicy> exclusionPolicies = new HashMap<>();

        AssessmentScoreCalculator toolCalculator = getAssessmentCalculator(0);
        RetryableTendersDBDelegate retryableTendersDBDelegate = context.getBean(RetryableTendersDBDelegate.class);
        for(AssessmentToolDimension atd : toolDimensionList){
            dimCalcList.put(atd.getDimension().getName(), getDimensionCalculator(atd));
            exclusionPolicies.put(atd.getDimension().getName(), getExclusionPolicy(atd));
        }

        return new BasicAssessmentToolCalculator(toolCalculator, dimCalcList, exclusionPolicies, retryableTendersDBDelegate);
    }



    private DimensionScoreCalculator getDimensionCalculator(AssessmentToolDimension atd){
        Integer id = atd.getCalculationRuleId();
        if(null == id)
            id = 0;
        if(id >= dimensionCalculator.size())
            throw new RuntimeException("Invalid Dimension Calculator Id " + id);

        return context.getBean(dimensionCalculator.get(id), DimensionScoreCalculator.class);
    }

    private ExclusionPolicy getExclusionPolicy(AssessmentToolDimension atd){
        Integer id = atd.getExclusionPolicyId();
        if(null == id)
            id = 0;
        if(id >= exclusionPolicy.size())
            throw new RuntimeException("Invalid Exclusion Policy Id " + id);

        return context.getBean(exclusionPolicy.get(id), ExclusionPolicy.class);
    }

    private AssessmentScoreCalculator getAssessmentCalculator(int id){
        if(id >= assessmentCalculator.size())
            throw new RuntimeException("Invalid AssessmentScore Calculator " + id);

        return context.getBean(assessmentCalculator.get(id), AssessmentScoreCalculator.class);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
