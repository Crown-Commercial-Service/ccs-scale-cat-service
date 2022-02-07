package uk.gov.crowncommercial.dts.scale.cat.mapper;

import java.util.Objects;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

/**
 * Encapsulates conversion logic between DTOs and Entities.
 */
@RequiredArgsConstructor
@Component
public class DependencyMapper {

	public QuestionNonOCDSDependency convertToQuestionNonOCDSDependency(final Requirement requirement) {

		final var questionNonOCDSDependency = new QuestionNonOCDSDependency();
		final var dependency = requirement.getNonOCDS().getDependency();
		if (Objects.nonNull(dependency.getConditional())) {
			questionNonOCDSDependency.conditional(new QuestionDependancy()
					.dependencyType(
							DependencyType.valueOf(dependency.getConditional().getDependencyType().getValue()))
					.dependencyValue(dependency.getConditional().getDependencyValue())
					.dependentOnId(dependency.getConditional().getDependentOnID()));
		}
		if (Objects.nonNull(dependency.getRelationships())) {
			questionNonOCDSDependency.relationships(
					new QuestionRelationship()
					.dependentOnId(dependency.getRelationships().getDependentOnID())
					.relationshipType(RelationshipType.fromValue(dependency.getRelationships().getRelationshipType()))
					);
		}
		return questionNonOCDSDependency;
	}

}
