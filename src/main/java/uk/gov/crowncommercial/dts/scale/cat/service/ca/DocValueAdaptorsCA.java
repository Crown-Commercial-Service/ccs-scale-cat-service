package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Criterion;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionDefinition;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.service.DocGenService;
import uk.gov.crowncommercial.dts.scale.cat.service.DocGenValueAdaptor;

/**
 * Document value adaptors for Capability Assessment data retrieval
 */
@Configuration
@RequiredArgsConstructor
public class DocValueAdaptorsCA {

  enum RequirementProperty {
    CLUSTER, FAMILY, NAME, QNTY;
  }

  static final String DIMENSION_RESOURCE_QUANTITIES = "Resource Quantities";
  static final String DIMENSION_SERVICE_CAPABILITY = "Service Capability";
  static final String DIMENSION_LOCATION = "Location";
  static final String DIMENSION_SC = "Security Clearance";

  static final String CACHE_KEY_ASSESSMENT = "CACHE_KEY_ASSESSMENT";
  static final String CACHE_KEY_PREFIX_DIMENSION_DEFS = "CACHE_KEY_DIMENSION_DEFS_";

  private final AssessmentService assessmentService;

  @Bean("DocumentValueAdaptorResQntyDDaTCluster")
  public DocGenValueAdaptor documentValueAdaptorResQntyDDaTCluster() {
    return (event, requestCache) -> {
      var assessment = getAssessment(event.getAssessmentId(), requestCache);
      var dimensionDefinitions = getDimensionDefinition(assessment.getExternalToolId(),
          DIMENSION_RESOURCE_QUANTITIES, requestCache);

      return getCAPlaceholderValues(assessment, dimensionDefinitions, RequirementProperty.CLUSTER);
    };
  }

  @Bean("DocumentValueAdaptorResQntyDDaTFamily")
  public DocGenValueAdaptor documentValueAdaptorResQntyDDaTFamily() {
    return (event, requestCache) -> {

      var assessment = getAssessment(event.getAssessmentId(), requestCache);
      var dimensionDefinitions = getDimensionDefinition(assessment.getExternalToolId(),
          DIMENSION_RESOURCE_QUANTITIES, requestCache);

      return getCAPlaceholderValues(assessment, dimensionDefinitions, RequirementProperty.FAMILY);
    };
  }

  @Bean("DocumentValueAdaptorResQntyDDaTRole")
  public DocGenValueAdaptor documentValueAdaptorResQntyDDaTRole() {
    return (event, requestCache) -> {
      var assessment = getAssessment(event.getAssessmentId(), requestCache);
      var dimensionDefinitions = getDimensionDefinition(assessment.getExternalToolId(),
          DIMENSION_RESOURCE_QUANTITIES, requestCache);

      return getCAPlaceholderValues(assessment, dimensionDefinitions, RequirementProperty.NAME);
    };
  }

  @Bean("DocumentValueAdaptorResQntyDDaTRoleQnty")
  public DocGenValueAdaptor documentValueAdaptorResQntyDDaTRoleQnty() {
    return (event, requestCache) -> {
      var assessment = getAssessment(event.getAssessmentId(), requestCache);
      var dimensionDefinitions = getDimensionDefinition(assessment.getExternalToolId(),
          DIMENSION_RESOURCE_QUANTITIES, requestCache);

      return getCAPlaceholderValues(assessment, dimensionDefinitions, RequirementProperty.QNTY);
    };
  }

  @Bean("DocumentValueAdaptorSvcCapDomain")
  public DocGenValueAdaptor documentValueAdaptorSvcCapDomain() {
    return (event, requestCache) -> {
      var assessment = getAssessment(event.getAssessmentId(), requestCache);
      var dimensionDefinitions = getDimensionDefinition(assessment.getExternalToolId(),
          DIMENSION_SERVICE_CAPABILITY, requestCache);

      return getCAPlaceholderValues(assessment, dimensionDefinitions, RequirementProperty.CLUSTER);
    };
  }

  @Bean("DocumentValueAdaptorSvcCapName")
  public DocGenValueAdaptor documentValueAdaptorSvcCapName() {
    return (event, requestCache) -> {
      var assessment = getAssessment(event.getAssessmentId(), requestCache);
      var dimensionDefinitions = getDimensionDefinition(assessment.getExternalToolId(),
          DIMENSION_SERVICE_CAPABILITY, requestCache);

      return getCAPlaceholderValues(assessment, dimensionDefinitions, RequirementProperty.NAME);
    };
  }

  @Bean("DocumentValueAdaptorLocationName")
  public DocGenValueAdaptor documentValueAdaptorLocationName() {
    return (event, requestCache) -> {
      var assessment = getAssessment(event.getAssessmentId(), requestCache);
      var dimensionDefinitions =
          getDimensionDefinition(assessment.getExternalToolId(), DIMENSION_LOCATION, requestCache);
      return getCAPlaceholderValues(assessment, dimensionDefinitions, RequirementProperty.NAME);
    };
  }

  @Bean("DocumentValueAdaptorHighestVettingLevel")
  public DocGenValueAdaptor documentValueAdaptorHighestVettingLevel() {
    return (event, requestCache) -> {
      var assessment = getAssessment(event.getAssessmentId(), requestCache);
      var placeholderValues = new ArrayList<String>();

      /*
       * Attempts to find the highest vetting level required based on order e.g. '4: Developed
       * Vetting (DV)' (highest) to '0: None' (lowest)
       */
      assessment.getDimensionRequirements().stream().filter(dr -> DIMENSION_SC.equals(dr.getName()))
          .findFirst().ifPresentOrElse(dr -> {
            placeholderValues
                .add(dr.getRequirements().stream().flatMap(rqmt -> rqmt.getValues().stream())
                    .map(Criterion::getValue).sorted(Comparator.reverseOrder()).findFirst()
                    .orElse(DocGenService.PLACEHOLDER_UNKNOWN));
          }, () -> placeholderValues.add(DocGenService.PLACEHOLDER_UNKNOWN));

      return new ArrayList<>();
    };
  }

  private Assessment getAssessment(final Integer assessmentId,
      final ConcurrentMap<String, Object> requestCache) {
    return (Assessment) requestCache.computeIfAbsent(CACHE_KEY_ASSESSMENT,
        k -> assessmentService.getAssessment(assessmentId, Optional.empty()));
  }

  private DimensionDefinition getDimensionDefinition(final String toolId, final String dimension,
      final ConcurrentMap<String, Object> requestCache) {
    return (DimensionDefinition) requestCache.computeIfAbsent(
        CACHE_KEY_PREFIX_DIMENSION_DEFS + dimension,
        k -> assessmentService.getDimensions(Integer.valueOf(toolId)).stream()
            .filter(dd -> Objects.equals(dd.getName(), dimension)).findFirst().orElseThrow());
  }

  private List<String> getCAPlaceholderValues(final Assessment assessment,
      final DimensionDefinition dimensionDefinitions, final RequirementProperty ddatProperty) {
    var placeholderValues = new ArrayList<String>();

    assessment.getDimensionRequirements().stream()
        .filter(dr -> Objects.equals(dimensionDefinitions.getName(), dr.getName())).findFirst()
        .ifPresent(dimensionResQnts -> {

          /*
           * For each requirement ID, return the requirement group name or fetch the assessment
           * taxon group hierarchy by level. Sort by requirement ID to ensure alignment between
           * placeholders in different columns of the same table.
           */
          dimensionResQnts.getRequirements().stream()
              .sorted(Comparator.comparing(Requirement::getRequirementId)).forEach(rqmt -> {

                if (ddatProperty == RequirementProperty.QNTY) {
                  // TODO: UI is currently persisting value in requirement weighting, should be in
                  // value array
                  placeholderValues.add(
                      Objects.toString(rqmt.getWeighting(), DocGenService.PLACEHOLDER_UNKNOWN));
                } else if (ddatProperty == RequirementProperty.NAME) {
                  placeholderValues.add(rqmt.getName());
                } else {

                  // Use the dimension definition to get cluster or role family
                  var rqmtOption = dimensionDefinitions.getOptions().stream().filter(
                      dimOpt -> Objects.equals(dimOpt.getRequirementId(), rqmt.getRequirementId()))
                      .findFirst().orElseThrow();

                  placeholderValues.add(rqmtOption.getGroups().stream().filter(
                      g -> g.getLevel() == (ddatProperty == RequirementProperty.CLUSTER ? 1 : 2))
                      .findFirst().orElseThrow().getName());
                }
              });
        });
    return placeholderValues;
  }

}
