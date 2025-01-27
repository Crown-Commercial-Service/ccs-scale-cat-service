package uk.gov.crowncommercial.dts.scale.cat.mapper;

import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DependencyType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionDependancy;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionNonOCDSDependency;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionRelationship;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RelationshipType;

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
							DependencyType.fromValue(dependency.getConditional().getDependencyType().getValue()))
					.dependencyValue(dependency.getConditional().getDependencyValue())
					.dependentOnId(dependency.getConditional().getDependentOnID()));
		}
		if (Objects.nonNull(dependency.getRelationships())) {
			final var relationshipList = dependency.getRelationships().stream()
					.map(relationships -> new QuestionRelationship()
					.dependentOnId(relationships.getDependentOnID())
					.relationshipType(RelationshipType.fromValue(relationships.getRelationshipType()))
					).collect(Collectors.toList());
			questionNonOCDSDependency.relationships(relationshipList);
		}
		return questionNonOCDSDependency;
	}

}
