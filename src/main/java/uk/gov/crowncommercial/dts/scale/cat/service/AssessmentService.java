package uk.gov.crowncommercial.dts.scale.cat.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionDefinition.TypeEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.*;
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

    Set<DimensionEntity> dimensions = retryableTendersDBDelegate.findDimensionsByToolId(toolId);

    return dimensions.stream().map(d -> {
      var dd = new DimensionDefinition();
      dd.setName(d.getName());
      dd.setType(TypeEnum.INTEGER); // TODO

      var wr = new WeightingRange();
      wr.setMin(d.getMinWeightingPercentage().intValue());
      wr.setMax(d.getMaxWeightingPercentage().intValue());
      dd.setWeightingRange(wr);

      List<DimensionOption> options = new ArrayList<>();

      d.getAssessmentTaxons().stream()
          .forEach(at -> at.getRequirementTaxons().stream().forEach(rt -> {
            var dopt = new DimensionOption();
            dopt.setName(rt.getRequirement().getName());
            var doptg = new DimensionOptionGroups();
            doptg.setName(at.getName());
            doptg.setLevel(1); // TODO - may need to expand this?
            dopt.setGroups(Arrays.asList(doptg));
            options.add(dopt);
          }));

      dd.setOptions(options);


      return dd;
    }).collect(Collectors.toSet());
  }

  /**
   *
   * @param assessment
   * @param principal
   * @return
   */
  public Integer createAssessment(final Assessment assessment, final String principal) {

    AssessmentTool tool =
        retryableTendersDBDelegate.findAssessmentToolByExternalToolId(assessment.getToolId())
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format(ERR_FMT_TOOL_NOT_FOUND, assessment.getToolId())));

    var entity = new AssessmentEntity();
    entity.setTool(tool);
    entity.setStatus("Status"); // ?
    entity.setBuyerOrganisationId(1); // ?
    entity.setTimestamps(createTimestamps(principal));

    Set<AssessmentSelection> assessmentSelections = new HashSet<>();

    // TODO: needs some validation that Dimensions are valid for that Tool
    Set<AssessmentDimensionWeighting> dimensionWeightings =
        assessment.getDimensionRequirements().stream().map(dr -> {

          var dimension = retryableTendersDBDelegate.findDimensionByName(dr.getName())
              .orElseThrow(() -> new ResourceNotFoundException(
                  String.format(ERR_FMT_DIMENSION_NOT_FOUND, dr.getName())));

          // Create Dimension Weightings
          var dimensionWeighting = new AssessmentDimensionWeighting();
          dimensionWeighting.setAssessment(entity);
          dimensionWeighting.setDimension(dimension);
          dimensionWeighting.setWeightingPercentage(new BigDecimal(dr.getWeighting()));
          dimensionWeighting.setTimestamps(createTimestamps(principal));

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
              as.setAssessment(entity);
              as.setDimension(dimension);
              as.setWeightingPercentage(new BigDecimal(r.getWeighting()));
              as.setTimestamps(createTimestamps(principal));
              as.setRequirementTaxon(requirementTaxon);
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
      summary.setToolId(a.getTool().getExternalToolId());
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
    response.setToolId(assessment.getTool().getExternalToolId());
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
