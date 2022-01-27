package uk.gov.crowncommercial.dts.scale.cat.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentDimensionWeighting;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentSelection;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentService {

  private static final String ERR_FMT_TOOL_NOT_FOUND = "Assessment Tool [%s] not found";
  private static final String ERR_FMT_ASSESSMENT_NOT_FOUND = "Assessment [%s] not found";
  private static final String ERR_FMT_DIMENSION_NOT_FOUND = "Dimension [%s] not found";
  private static final String ERR_FMT_REQUIREMENT_TAXON_NOT_FOUND =
      "Requirement Taxon for Requirement [%s] and Tool [%s] not found";

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  /**
   *
   * @param toolId
   * @return
   */
  public Set<DimensionDefinition> getDimensions(final Integer toolId) {

    // Set<DimensionEntity> dimensions = retryableTendersDBDelegate.findDimensionsByToolId(toolId);

    // return dimensions.stream().map(d -> {
    // var dd = new DimensionDefinition();
    // dd.setName(d.getName());
    // return dd;
    // }).collect(Collectors.toSet());
    return new HashSet<DimensionDefinition>();
  }

  /**
   *
   * @param assessment
   * @param principal
   * @return
   */
  public Integer createAssessment(final Assessment assessment, final String principal) {

    // TODO - can we change 'internalName' to internalId/externalId/key or something maybe? Do we
    // need to change API reference? Have we a ticket to add this to Agreements Service?
    AssessmentTool tool =
        retryableTendersDBDelegate.findAssessmentToolByInternalName(assessment.getToolId())
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format(ERR_FMT_TOOL_NOT_FOUND, assessment.getToolId())));

    var entity = new AssessmentEntity();
    entity.setTool(tool);
    entity.setStatus("Status"); // ?
    entity.setBuyerOrganisationId(1); // ?
    entity.setTimestamps(createTimestamps(principal));


    Set<AssessmentSelection> assessmentSelections = new HashSet<>();

    // Save dimension weightings
    // TODO: needs some validation that Dimensions are valid for that Tool
    Set<AssessmentDimensionWeighting> dimensionWeightings =
        assessment.getDimensionRequirements().stream().map(dr -> {

          var dimension = retryableTendersDBDelegate.findDimensionByName(dr.getName())
              .orElseThrow(() -> new ResourceNotFoundException(
                  String.format(ERR_FMT_DIMENSION_NOT_FOUND, dr.getName())));

          // Create Dimension Weightings
          var dimensionWeighting = new AssessmentDimensionWeighting();
          dimensionWeighting.setAssessmentId(assessment.getAssesmentId());
          dimensionWeighting.setDimension(dimension);
          dimensionWeighting.setWeightingPercentage(new BigDecimal(dr.getWeighting()));
          dimensionWeighting.setTimestamps(createTimestamps(principal));
          // retryableTendersDBDelegate.save(selection);

          if (dr.getRequirements() != null) {
            assessmentSelections.addAll(dr.getRequirements().stream().map(r -> {

              log.info("Find Requirement");

              var requirementTaxon =
                  retryableTendersDBDelegate
                      .findRequirementTaxon(r.getRequirementId(), tool.getId())
                      .orElseThrow(() -> new ResourceNotFoundException(
                          String.format(ERR_FMT_REQUIREMENT_TAXON_NOT_FOUND, r.getRequirementId(),
                              tool.getId())));

              var as = new AssessmentSelection();
              // as.setAssessment(entity);
              as.setDimension(dimension);
              as.setWeightingPercentage(new BigDecimal(r.getWeighting()));
              as.setTimestamps(createTimestamps(principal));
              as.setRequirementTaxon(requirementTaxon);
              // retryableTendersDBDelegate.save(as);
              return as;
            }).collect(Collectors.toSet()));
          }

          return dimensionWeighting;
        }

        ).collect(Collectors.toSet());

    entity.setAssessmentSelections(assessmentSelections);
    entity.setDimensionWeightings(dimensionWeightings);

    return retryableTendersDBDelegate.save(entity).getId().intValue();
  }

  /**
   *
   * @param principal
   * @return
   */
  public List<AssessmentSummary> getAssessmentsForUser(final String principal) {
    Set<AssessmentEntity> assessments =
        retryableTendersDBDelegate.findAssessmentsForUser(principal);

    return assessments.stream().map(a -> {
      var summary = new AssessmentSummary();
      summary.setAssessmentId(a.getId());
      summary.setToolId(a.getTool().getInternalName());
      return summary;
    }).collect(Collectors.toList());
  }

  /**
   *
   * @param assessmentId
   * @param principal
   * @return
   */
  public Assessment getAssessment(final Integer assessmentId, final String principal) {

    AssessmentEntity assessment = retryableTendersDBDelegate.findAssessmentById(assessmentId)
        .orElseThrow(() -> new ResourceNotFoundException(
            String.format(ERR_FMT_ASSESSMENT_NOT_FOUND, assessmentId)));

    if (!assessment.getTimestamps().getCreatedBy().equals(principal)) {
      throw new AuthorisationFailureException("User is not authorised to view that Assessment");
    }

    var dimensions = assessment.getDimensionWeightings().stream().map(dw -> {
      var req = new DimensionRequirement();
      req.setName(dw.getDimension().getName());
      req.setWeighting(dw.getWeightingPercentage().intValue());

      var requirements = assessment.getAssessmentSelections().stream()
          .filter(s -> s.getDimension().getName().equals(dw.getDimension().getName())).map(s -> {
            var requirement = new Requirement();
            requirement.setName(s.getRequirementTaxon().getRequirement().getName());
            return requirement;
          }).collect(Collectors.toList());

      req.setRequirements(requirements);
      return req;

    }).collect(Collectors.toList());

    var response = new Assessment();
    response.setToolId(assessment.getTool().getInternalName());
    response.setAssesmentId(assessmentId);
    response.setDimensionRequirements(dimensions);

    return response;
  }

  /**
   * TEMP
   */
  private Timestamps createTimestamps(final String userId) {
    var ts = new Timestamps();
    ts.setCreatedAt(Instant.now());
    ts.setCreatedBy(userId);
    return ts;
  }

}
