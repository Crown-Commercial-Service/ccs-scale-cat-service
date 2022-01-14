package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.lang.String.format;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.validation.ValidationException;
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
  static final String ERR_MSG_FMT_CONCLAVE_USER_MISSING = "User [%s] not found in Conclave";

  private final ConclaveService conclaveService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  /**
   * Get Dimensions for Assessment Tool.
   *
   * @param toolId internal database AssessmentTool id
   * @return
   */
  public Set<DimensionDefinition> getDimensions(final Integer toolId) {

    // Explicitly validate toolId so we can throw a 404 (otherwise empty array returned)
    AssessmentTool tool = retryableTendersDBDelegate.findAssessmentToolById(toolId).orElseThrow(
        () -> new ResourceNotFoundException(String.format(ERR_FMT_TOOL_NOT_FOUND, toolId)));

    Set<DimensionEntity> dimensions =
        retryableTendersDBDelegate.findDimensionsByToolId(tool.getId());

    return dimensions.stream().map(d -> {

      // Build DimensionDefinition
      var dd = new DimensionDefinition();
      dd.setDimensionId(d.getId());
      dd.setName(d.getName());
      dd.setType(TypeEnum.fromValue(d.getSelectionType().toLowerCase()));

      var wr = new WeightingRange();
      wr.setMin(d.getMinWeightingPercentage().intValue());
      wr.setMax(d.getMaxWeightingPercentage().intValue());
      dd.setWeightingRange(wr);

      // Build Options
      List<DimensionOption> dimensionOptions = new ArrayList<>();
      d.getAssessmentTaxons().stream()
          .forEach(at -> at.getRequirementTaxons().stream().forEach(rt -> {
            var dopt = new DimensionOption();
            dopt.setName(rt.getRequirement().getName());
            var doptg = new DimensionOptionGroups();
            doptg.setName(at.getName());
            doptg.setLevel(calculateLevel(at, 0));
            dopt.setGroups(Arrays.asList(doptg));
            dimensionOptions.add(dopt);
          }));
      dd.setOptions(dimensionOptions);

      // Build Evaluation Criteria
      var submissionTypes = retryableTendersDBDelegate.findAllSubmissionTypes();
      List<CriterionDefinition> criteria = new ArrayList<>();

      /*
       * This will return the same list for each submission type - which is redundant as the
       * database does not support this currently, but Nick wanted to keep the option in the API for
       * these to be different in future if required (will require a change to the database and
       * either a different DB call, or filtering of this list).
       */
      var options = d.getValidValues().stream().map(DimensionValidValue::getValueName)
          .collect(Collectors.toList());

      submissionTypes.stream().forEach(st -> {
        var criterion = new CriterionDefinition();
        criterion.setName(st.getName());
        criterion.setType(null);
        criterion.setOptions(options);
        criteria.add(criterion);
      });
      dd.setEvaluationCriteria(criteria);

      return dd;

    }).collect(Collectors.toSet());
  }

  /**
   * Create an Assessment.
   *
   * @param assessment
   * @param principal
   * @return
   */
  public Integer createAssessment(final Assessment assessment, final String principal) {

    AssessmentTool tool = retryableTendersDBDelegate
        .findAssessmentToolByExternalToolId(assessment.getExternalToolId())
        .orElseThrow(() -> new ResourceNotFoundException(
            format(ERR_FMT_TOOL_NOT_FOUND, assessment.getExternalToolId())));

    var conclaveUser = conclaveService.getUserProfile(principal).orElseThrow(
        () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, principal)));


    var entity = new AssessmentEntity();
    entity.setTool(tool);
    entity.setStatus("TODO"); // TODO: Outstanding question on SCAT-2918
    entity.setBuyerOrganisationId(conclaveUser.getOrganisationId());
    entity.setTimestamps(createTimestamps(principal));

    Set<AssessmentSelection> assessmentSelections = new HashSet<>();

    Set<AssessmentDimensionWeighting> dimensionWeightings =
        assessment.getDimensionRequirements().stream().map(dr -> {

          var dimension = retryableTendersDBDelegate.findDimensionByName(dr.getName())
              .orElseThrow(() -> new ResourceNotFoundException(
                  String.format(ERR_FMT_DIMENSION_NOT_FOUND, dr.getName())));

          // Validation
          var dimensionTool = dimension.getAssessmentTaxons().stream().findFirst()
              .orElseThrow(() -> new ResourceNotFoundException(
                  format(ERR_FMT_TOOL_NOT_FOUND, assessment.getExternalToolId())))
              .getTool();

          if (!dimensionTool.getId().equals(tool.getId())) {
            throw new ValidationException("Invalid requirement - XXX - TODO");
          }

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
                  retryableTendersDBDelegate.findRequirementTaxon(r.getName(), tool.getId())
                      .orElseThrow(() -> new ResourceNotFoundException(
                          format(ERR_FMT_REQUIREMENT_TAXON_NOT_FOUND, r.getRequirementId(),
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
   * Get Assessments for a User.
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
      summary.setExternalToolId(a.getTool().getExternalToolId());
      return summary;
    }).collect(Collectors.toList());
  }

  /**
   * Get Assessment details.
   *
   * @param assessmentId
   * @param principal
   * @return
   */
  public Assessment getAssessment(final Integer assessmentId, final String principal) {

    AssessmentEntity assessment = retryableTendersDBDelegate.findAssessmentById(assessmentId)
        .orElseThrow(() -> new ResourceNotFoundException(
            format(ERR_FMT_ASSESSMENT_NOT_FOUND, assessmentId)));

    if (!assessment.getTimestamps().getCreatedBy().equals(principal)) {
      throw new AuthorisationFailureException("User is not authorised to view that Assessment");
    }

    var dimensions = assessment.getDimensionWeightings().stream().map(dw -> {
      var req = new DimensionRequirement();
      req.setName(dw.getDimension().getName());
      req.setType(dw.getDimension().getSelectionType());
      req.setWeighting(dw.getWeightingPercentage().intValue());

      var requirements = assessment.getAssessmentSelections().stream()
          .filter(s -> s.getDimension().getName().equals(dw.getDimension().getName())).map(s -> {
            var requirement = new Requirement();
            requirement.setName(s.getRequirementTaxon().getRequirement().getName());
            requirement.setWeighting(s.getWeightingPercentage().intValue());

            // TODO - ? what are the values
            // requirement.setValues(null);

            return requirement;
          }).collect(Collectors.toList());

      req.setRequirements(requirements);
      return req;

    }).collect(Collectors.toList());

    var response = new Assessment();
    response.setExternalToolId(assessment.getTool().getExternalToolId());
    response.setAssesmentId(assessmentId);
    response.setDimensionRequirements(dimensions);

    return response;
  }

  /**
   * Create entity date/times.
   */
  private Timestamps createTimestamps(final String userId) {
    var ts = new Timestamps();
    ts.setCreatedAt(Instant.now());
    ts.setCreatedBy(userId);
    return ts;
  }

  /**
   * Recursive routine to calculate nested level of AssessmentTaxon.
   *
   * @param taxon
   * @param level
   * @return
   */
  private int calculateLevel(final AssessmentTaxon taxon, int level) {
    if (taxon.getParentTaxon() == null) {
      return level;
    } else {
      var parent = taxon.getParentTaxon();
      if (parent == null) {
        return ++level;
      } else {
        return calculateLevel(taxon.getParentTaxon(), ++level);
      }
    }
  }
}
