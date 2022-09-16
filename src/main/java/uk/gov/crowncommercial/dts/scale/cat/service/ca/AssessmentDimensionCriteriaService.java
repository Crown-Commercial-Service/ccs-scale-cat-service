package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.CriteriaSelectionType;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.CriterionDefinition;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.AssessmentDimensionCriteriaRepo;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssessmentDimensionCriteriaService {
    private final AssessmentDimensionCriteriaRepo adcRepo;

    public Map<String, List<CriterionDefinition>> getCriterionDefinition(AssessmentEntity assessment) {
        List<AssessmentDimensionCriteria> criterias = adcRepo.findByAssessmentId(assessment.getId());

        Set<AssessmentDimensionWeighting> dimensionWeightings = assessment.getDimensionWeightings();
        Map<String, List<CriterionDefinition>> result = new HashMap<>();
        for (AssessmentDimensionWeighting adw : dimensionWeightings) {
            List<CriterionDefinition> defs = getAssessmentDimensionCriteria(adw, criterias);
            result.put(adw.getDimension().getName(), defs);
        }

        return result;
    }

    public void save(List<CriterionDefinition> criterionDefinitions, AssessmentEntity assessment, DimensionEntity dimension, String principal){

        List<AssessmentDimensionCriteria> adcList =adcRepo.findByAssessmentIdAndDimensionId(assessment.getId(), dimension.getId());
        List<Integer> processedIds = new ArrayList<>();

        if(null == criterionDefinitions || 0 == criterionDefinitions.size())
            return;

        for(CriterionDefinition def : criterionDefinitions){
            Integer criterionId = getCriterionId(def);
            AssessmentDimensionCriteria adc = adcList.stream().filter(d -> d.getCriterionId() == criterionId).findFirst().orElse(null);
            processedIds.add(criterionId);
            if(null == adc){
                adc = new AssessmentDimensionCriteria();
                adc.setActive(true);
                adc.setAssessment(assessment);
                adc.setDimension(dimension);
                adc.setCriterionId(criterionId);
                adc.setTimestamps(Timestamps.createTimestamps(principal));
            }else{
                if(null == adc.getActive() || !adc.getActive()) {
                    adc.setActive(true);
                    adc.setTimestamps(Timestamps.updateTimestamps(adc.getTimestamps(), principal));
                }
            }
            adcRepo.save(adc);
        }

        for(AssessmentDimensionCriteria adc : adcList){
            if(! processedIds.contains(adc.getCriterionId())){
                if(null != adc.getActive() && adc.getActive()) {
                    adc.setActive(false);
                    adc.setTimestamps(Timestamps.updateTimestamps(adc.getTimestamps(), principal));
                    adcRepo.save(adc);
                }
            }
        }
    }

    private Integer getCriterionId(CriterionDefinition def) {
        try {
            return Integer.valueOf(def.getCriterionId());
        }catch(NumberFormatException nfe){
            throw new RuntimeException("Invalid Criterion Id " + def.getCriterionId());
        }
    }

    private List<Integer> getCriteriaIdByDimension(List<AssessmentDimensionCriteria> criterias, Integer dimensionId) {
        return criterias.stream().filter(d -> (d.getDimension().getId().equals(dimensionId))).
                map(d -> d.getDimension().getId()).
                collect(Collectors.toList());
    }

    public List<CriterionDefinition> getAssessmentDimensionCriteria(
            final AssessmentDimensionWeighting assessmentDimensionWeighting,
            List<AssessmentDimensionCriteria> criterias) {

        return buildCriteria(assessmentDimensionWeighting.getDimension(),
                assessmentDimensionWeighting.getDimensionSubmissionTypes(),
                criterias);
    }

    private boolean filter(DimensionSubmissionType dst, List<Integer> dbCriterias){
        if(dbCriterias.size() > 0){
            return dbCriterias.contains(dst.getDimension().getId());
        }else{
            return true;
        }
    }

    private List<CriterionDefinition> buildCriteria(final DimensionEntity dimension,
                                                    final Collection<DimensionSubmissionType> dimensionSubmissionTypes,
                                                    List<AssessmentDimensionCriteria> criterias) {
        List<CriterionDefinition> result = new ArrayList<>();

        List<Integer> dbCriterias = getCriteriaIdByDimension(criterias, dimension.getId());

        var options = dimension.getValidValues().stream().map(DimensionValidValue::getValueName)
                .collect(Collectors.toList());

        dimensionSubmissionTypes.stream().filter(t -> filter(t, dbCriterias)).forEach(st -> {
            var criterion = new CriterionDefinition();
            var selectionType = CriteriaSelectionType.fromValue(st.getSelectionType().toLowerCase());
            criterion.setCriterionId(st.getSubmissionType().getCode());
            criterion.setName(st.getSubmissionType().getName());
            criterion.setType(selectionType);
            criterion.setMandatory(st.getMandatory());
            if (CriteriaSelectionType.SELECT == selectionType
                    || CriteriaSelectionType.MULTISELECT == selectionType) {
                criterion.setOptions(options);
            }
            result.add(criterion);
        });

        return result;
    }
}

