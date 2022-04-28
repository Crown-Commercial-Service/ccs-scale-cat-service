package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import java.util.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Criterion;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.repo.RequirementTaxonRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.DocGenService;
import uk.gov.crowncommercial.dts.scale.cat.service.DocGenValueAdaptor;

/**
 * Document value adaptors for Capability Assessment data retrieval
 */
@Configuration
@RequiredArgsConstructor
public class DocValueAdaptorsCA {

  enum RequirementProperty {
    CLUSTER, ROLE_FAMILY, ROLE, QNTY;
  }

  static final String DIMENSION_RESOURCE_QUANTITIES = "Resource Quantities";
  static final String DIMENSION_SERVICE_CAPABILITY = "Service Capabilty";
  static final String DIMENSION_LOCATION = "Location";
  static final String DIMENSION_SC = "Security Clearance";

  private final AssessmentService assessmentService;
  private final RequirementTaxonRepo requirementTaxonRepo;

  @Bean("DocumentValueAdaptorResQntyDDaTCluster")
  public DocGenValueAdaptor documentValueAdaptorResQntyDDaTCluster() {
    return (event, requestCache) -> {

      // Get assessment, scores not required (e.g for FC) - bit of a cheat, could go straight to DB
      var assessment = assessmentService.getAssessment(event.getAssessmentId(), Optional.empty());
      return getCAPlaceholderValues(assessment, DIMENSION_RESOURCE_QUANTITIES,
          RequirementProperty.CLUSTER);
    };
  }

  @Bean("documentValueAdaptorResQntyDDaTFamily")
  public DocGenValueAdaptor documentValueAdaptorResQntyDDaTFamily() {
    return (event, requestCache) -> {
      var assessment = assessmentService.getAssessment(event.getAssessmentId(), Optional.empty());
      return getCAPlaceholderValues(assessment, DIMENSION_RESOURCE_QUANTITIES,
          RequirementProperty.ROLE_FAMILY);
    };
  }

  @Bean("DocumentValueAdaptorResQntyDDaTRole")
  public DocGenValueAdaptor documentValueAdaptorResQntyDDaTRole() {
    return (event, requestCache) -> {
      var assessment = assessmentService.getAssessment(event.getAssessmentId(), Optional.empty());
      return getCAPlaceholderValues(assessment, DIMENSION_RESOURCE_QUANTITIES,
          RequirementProperty.ROLE);
    };
  }

  @Bean("DocumentValueAdaptorResQntyDDaTRoleQnty")
  public DocGenValueAdaptor documentValueAdaptorResQntyDDaTRoleQnty() {
    return (event, requestCache) -> {
      var assessment = assessmentService.getAssessment(event.getAssessmentId(), Optional.empty());
      return getCAPlaceholderValues(assessment, DIMENSION_RESOURCE_QUANTITIES,
          RequirementProperty.QNTY);
    };
  }

  @Bean("DocumentValueAdaptorSvcCapDomain")
  public DocGenValueAdaptor documentValueAdaptorSvcCapDomain() {
    return (event, requestCache) -> {
      var assessment = assessmentService.getAssessment(event.getAssessmentId(), Optional.empty());
      return getCAPlaceholderValues(assessment, DIMENSION_SERVICE_CAPABILITY,
          RequirementProperty.CLUSTER);
    };
  }

  @Bean("DocumentValueAdaptorSvcCapName")
  public DocGenValueAdaptor documentValueAdaptorSvcCapName() {
    return (event, requestCache) -> {
      var assessment = assessmentService.getAssessment(event.getAssessmentId(), Optional.empty());
      return getCAPlaceholderValues(assessment, DIMENSION_SERVICE_CAPABILITY,
          RequirementProperty.ROLE);
    };
  }

  @Bean("DocumentValueAdaptorLocationName")
  public DocGenValueAdaptor documentValueAdaptorLocationName() {
    return (event, requestCache) -> {
      var assessment = assessmentService.getAssessment(event.getAssessmentId(), Optional.empty());
      return getCAPlaceholderValues(assessment, DIMENSION_LOCATION, RequirementProperty.ROLE);
    };
  }

  @Bean("DocumentValueAdaptorHighestVettingLevel")
  public DocGenValueAdaptor documentValueAdaptorHighestVettingLevel() {
    return (event, requestCache) -> {
      var assessment = assessmentService.getAssessment(event.getAssessmentId(), Optional.empty());
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

  private List<String> getCAPlaceholderValues(final Assessment assessment, final String dimension,
      final RequirementProperty ddatProperty) {
    var placeholderValues = new ArrayList<String>();

    assessment.getDimensionRequirements().stream().filter(dr -> dimension.equals(dr.getName()))
        .findFirst().ifPresent(dimensionResQnts -> {

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
                } else if (ddatProperty == RequirementProperty.ROLE) {
                  placeholderValues.add(rqmt.getName());
                } else {
                  var requirementTaxon = requirementTaxonRepo.getById(rqmt.getRequirementId());
                  var groups = assessmentService.recurseUpTree(requirementTaxon.getTaxon(),
                      new ArrayList<>());

                  placeholderValues.add(groups.stream().filter(
                      g -> g.getLevel() == (ddatProperty == RequirementProperty.CLUSTER ? 1 : 2))
                      .findFirst().orElseThrow().getName());
                }
              });
        });
    return placeholderValues;
  }

  public static void main(final String[] args) {
    List.of("0: None", "1: Baseline Personnel Security Standard (BPSS)",
        "2: Counter Terrorist Check (CTC)", "3: Security Check (SC)", "4: Developed Vetting (DV)")
        .stream().sorted(Comparator.reverseOrder()).forEach(System.out::println);
  }

}
