package uk.gov.crowncommercial.dts.scale.cat.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DependencyType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionDependancy;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionNonOCDSDependency;

import java.util.Arrays;
import java.util.Objects;

/**
 * Encapsulates conversion logic between DTOs and Entities.
 */
@RequiredArgsConstructor
@Component
public class DependencyMapper {

    public QuestionNonOCDSDependency convertToQuestionNonOCDSDependency(final Requirement requirement) {

        var questionNonOCDSDependency = new QuestionNonOCDSDependency();

        if (Objects.nonNull(requirement.getNonOCDS().getDependency().getConditional())) {
            questionNonOCDSDependency.conditional(new QuestionDependancy()
                    .dependencyType(
                            DependencyType.valueOf(requirement.getNonOCDS().
                                    getDependency().getConditional().getDependencyType().getValue()))
                    .dependencyValue(requirement.getNonOCDS().getDependency().getConditional().getDependencyValue())
                    .dependentOnId(requirement.getNonOCDS().getDependency().getConditional().getDependentOnID()));
        }
        if (Objects.nonNull(requirement.getNonOCDS().
                getDependency().getRelationships())) {
            questionNonOCDSDependency.relationships(Arrays.asList(
                    requirement.getNonOCDS().getDependency().getRelationships().getRelationshipType(),
                    requirement.getNonOCDS().getDependency().getRelationships().getDependentOnID())
            );
        }
        return questionNonOCDSDependency;
    }

}
