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
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;
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
  private static final String ERR_MSG_FMT_CONCLAVE_USER_MISSING = "User [%s] not found in Conclave";
  private static final String ERR_MSG_FMT_DIMENSION_VALID_VALUE_NOT_FOUND =
      "A valid value matching [%s] not found for dimension [%s]";
  private static final String ERR_FMT_SUBMISSION_TYPE_NOT_FOUND =
      "Submission Type for Criterion [%s] not found";
  private static final String ERR_FMT_DIMENSION_SELECTION_TYPE_NOT_FOUND =
      "Dimension Select Type [%s] not found";

  private final ConclaveService conclaveService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final AgreementsService agreementsService;

  /**
   * Get the Dimensions for an Assessment Tool.
   *
   * @param toolId internal database Assessment Tool id
   * @return
   */
  public List<DimensionDefinition> getDimensions(final Integer toolId) {

    // Explicitly validate toolId so we can throw a 404 (otherwise empty array returned)
    var tool = retryableTendersDBDelegate.findAssessmentToolById(toolId).orElseThrow(
        () -> new ResourceNotFoundException(String.format(ERR_FMT_TOOL_NOT_FOUND, toolId)));

    var dimensions = retryableTendersDBDelegate.findDimensionsByToolId(tool.getId());

    return dimensions.stream().map(d -> {
      // Build DimensionDefinition
      var dd = new DimensionDefinition();
      dd.setDimensionId(d.getId());
      dd.setName(d.getName());
      dd.setType(DimensionSelectionType.fromValue(d.getSelectionType().toLowerCase()));

      // Build WeightingRange
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
            dopt.setGroups(List.of(doptg));
            dimensionOptions.add(dopt);
          }));
      dd.setOptions(dimensionOptions);

      // Build Evaluation Criteria
      dd.setEvaluationCriteria(getCriteriaForDimension(d, tool));

      return dd;

    }).collect(Collectors.toList());
  }

  /**
   * Creates an empty assessment by first querying the AS for the correct
   * <code>external-tool-id</code> based on the arguments
   *
   * @param caNumber
   * @param lotNumber
   * @param eventType
   * @return assessmentId for the newly created assessment
   */
  public Integer createEmptyAssessment(final String caNumber, final String lotNumber,
      final DefineEventType eventType, final String principal) {
    var lotEventType = agreementsService.getLotEventTypes(caNumber, lotNumber).stream()
        .filter(let -> eventType.name().equals(let.getType())).findFirst()
        .orElseThrow(IllegalStateException::new);

    var assessment = new Assessment().externalToolId(lotEventType.getAssessmentToolId());
    log.debug("Empty assessment created with ext tool-id [" + lotEventType.getAssessmentToolId()
        + "] for event type [" + eventType + "]");
    return createAssessment(assessment, principal);
  }

  /**
   * Create an Assessment.
   *
   * @param assessment
   * @param principal
   * @return
   */
  public Integer createAssessment(final Assessment assessment, final String principal) {

    var tool = retryableTendersDBDelegate
        .findAssessmentToolByExternalToolId(assessment.getExternalToolId())
        .orElseThrow(() -> new ResourceNotFoundException(
            format(ERR_FMT_TOOL_NOT_FOUND, assessment.getExternalToolId())));

    var conclaveUser = conclaveService.getUserProfile(principal).orElseThrow(
        () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, principal)));

    var entity = new AssessmentEntity();
    entity.setTool(tool);
    entity.setStatus(AssessmentStatus.ACTIVE);
    entity.setBuyerOrganisationId(conclaveUser.getOrganisationId());
    entity.setTimestamps(createTimestamps(principal));

    Set<AssessmentSelection> assessmentSelections = new HashSet<>();

    Set<AssessmentDimensionWeighting> dimensionWeightings =
        Optional.ofNullable(assessment.getDimensionRequirements()).orElse(Collections.emptyList())
            .stream().map(dr -> {

              var dimension = retryableTendersDBDelegate.findDimensionByName(dr.getName())
                  .orElseThrow(() -> new ResourceNotFoundException(
                      String.format(ERR_FMT_DIMENSION_NOT_FOUND, dr.getName())));

              // Build Dimension Weightings
              var dimensionWeighting = new AssessmentDimensionWeighting();
              dimensionWeighting.setAssessment(entity);
              dimensionWeighting.setDimension(dimension);
              dimensionWeighting.setWeightingPercentage(new BigDecimal(dr.getWeighting()));
              dimensionWeighting.setTimestamps(createTimestamps(principal));

              if (dr.getRequirements() != null) {
                assessmentSelections.addAll(dr.getRequirements().stream().map(r -> {

                  log.trace("Find RequirementTaxon");

                  var requirementTaxon =
                      retryableTendersDBDelegate.findRequirementTaxon(r.getName(), tool.getId())
                          .orElseThrow(() -> new ResourceNotFoundException(
                              format(ERR_FMT_REQUIREMENT_TAXON_NOT_FOUND, r.getRequirementId(),
                                  tool.getId())));

                  // Build AssessmentSelection
                  var as = new AssessmentSelection();
                  as.setAssessment(entity);
                  as.setDimension(dimension);
                  as.setWeightingPercentage(new BigDecimal(r.getWeighting()));
                  as.setTimestamps(createTimestamps(principal));
                  as.setRequirementTaxon(requirementTaxon);

                  // Build AssessmentSelectionDetails
                  if (r.getValues() != null) {
                    as.setAssessmentSelectionDetails(r.getValues().stream().map(c -> {
                      var asd = new AssessmentSelectionDetail();
                      asd.setAssessmentSelection(as);
                      var assessmentSubmissionType = tool.getAssessmentSubmissionTypes().stream()
                          .filter(
                              ast -> ast.getSubmissionType().getCode().equals(c.getCriterionId()))
                          .findFirst().orElseThrow(() -> new ValidationException(
                              format(ERR_FMT_SUBMISSION_TYPE_NOT_FOUND, c.getCriterionId())));
                      asd.setAssessmentSubmissionType(assessmentSubmissionType);
                      asd.setTimestamps(createTimestamps(principal));

                      var dimensionSelectionType = DimensionSelectionType
                          .fromValue(dimension.getSelectionType().toLowerCase());

                      switch (dimensionSelectionType) {
                        case SELECT:
                        case MULTISELECT:
                          var validValue = getValidValueByName(dimension, c.getValue());
                          asd.setRequirementValidValueCode(validValue.getKey().getValueCode());
                          break;
                        case INTEGER:
                          asd.setRequirementValue(new BigDecimal(c.getValue()));
                          break;
                        default:
                          throw new ValidationException(format(
                              ERR_FMT_DIMENSION_SELECTION_TYPE_NOT_FOUND, dimensionSelectionType));
                      }
                      return asd;
                    }).collect(Collectors.toSet()));
                  }

                  return as;
                }).collect(Collectors.toSet()));
              }

              return dimensionWeighting;
            }

            ).collect(Collectors.toSet());

    entity.setAssessmentSelections(assessmentSelections);
    entity.setDimensionWeightings(dimensionWeightings);

    return retryableTendersDBDelegate.save(entity).getId();

  }

  /**
   * Get Assessments for a User.
   *
   * @param principal
   * @return
   */
  public List<AssessmentSummary> getAssessmentsForUser(final String principal) {
    var assessments = retryableTendersDBDelegate.findAssessmentsForUser(principal);

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

    var assessment = retryableTendersDBDelegate.findAssessmentById(assessmentId).orElseThrow(
        () -> new ResourceNotFoundException(format(ERR_FMT_ASSESSMENT_NOT_FOUND, assessmentId)));

    if (!assessment.getTimestamps().getCreatedBy().equals(principal)) {
      throw new AuthorisationFailureException("User is not authorised to view that Assessment");
    }

    // Build DimensionRequirements
    var dimensions = assessment.getDimensionWeightings().stream().map(dw -> {

      var req = new DimensionRequirement();
      req.setDimensionId(dw.getDimension().getId());
      req.setName(dw.getDimension().getName());
      req.setType(dw.getDimension().getSelectionType());
      req.setWeighting(dw.getWeightingPercentage().intValue());

      // Build Criteria for Dimension (includedCriteria)
      req.setIncludedCriterion(getCriteriaForDimension(dw.getDimension(), assessment.getTool()));

      // Build Requirements
      var requirements = assessment.getAssessmentSelections().stream()
          .filter(s -> s.getDimension().getName().equals(dw.getDimension().getName())).map(s -> {
            var requirement = new Requirement();
            requirement.setName(s.getRequirementTaxon().getRequirement().getName());
            requirement.setWeighting(s.getWeightingPercentage().intValue());
            requirement.setRequirementId(s.getId());

            // Build Criteria for Requirement (values)
            requirement.setValues(s.getAssessmentSelectionDetails().stream().map(asd -> {
              var criterion = new Criterion();
              criterion
                  .setCriterionId(asd.getAssessmentSubmissionType().getSubmissionType().getCode());
              criterion.setName(asd.getAssessmentSubmissionType().getSubmissionType().getName());

              if (asd.getRequirementValidValueCode() != null) {
                var validValue =
                    getValidValueByCode(dw.getDimension(), asd.getRequirementValidValueCode());
                criterion.setValue(validValue.getValueName());
              } else {
                if (asd.getRequirementValue() != null) {
                  criterion.setValue(asd.getRequirementValue().toString());
                }
              }
              return criterion;
            }).collect(Collectors.toList()));

            return requirement;
          }).collect(Collectors.toList());

      req.setRequirements(requirements);

      return req;

    }).collect(Collectors.toList());

    // TODO: Calculate Scores
    List<SupplierScores> scores = new ArrayList<>();

    var response = new Assessment();
    response.setExternalToolId(assessment.getTool().getExternalToolId());
    response.setAssessmentId(assessmentId);
    response.setDimensionRequirements(dimensions);
    response.setScores(scores);

    return response;
  }

  /**
   * Create entity time stamps.
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
    }
    var parent = taxon.getParentTaxon();
    if (parent == null) {
      return ++level;
    }
    return calculateLevel(taxon.getParentTaxon(), ++level);
  }

  /**
   * Get Dimension Valid Values by Name (for incoming requests from the API).
   *
   * @param dimension Dimension
   * @param valueName Valid Value Name
   * @return
   */
  private DimensionValidValue getValidValueByName(final DimensionEntity dimension,
      final String valueName) {
    return dimension.getValidValues().stream().filter(vv -> vv.getValueName().equals(valueName))
        .findFirst().orElseThrow(() -> new ValidationException(
            format(ERR_MSG_FMT_DIMENSION_VALID_VALUE_NOT_FOUND, valueName, dimension.getName())));
  }

  /**
   * Get Dimension Valid Values by Code (for outgoing responses from the database).
   *
   * @param dimension Dimension
   * @param valueCode Valid Value Code
   * @return
   */
  private DimensionValidValue getValidValueByCode(final DimensionEntity dimension,
      final String valueCode) {
    return dimension.getValidValues().stream()
        .filter(vv -> vv.getKey().getValueCode().equals(valueCode)).findFirst()
        .orElseThrow(() -> new ValidationException(
            format(ERR_MSG_FMT_DIMENSION_VALID_VALUE_NOT_FOUND, valueCode, dimension.getName())));
  }

  /**
   * Get the Criteria for a Dimension.
   *
   * This will return the same list for each submission type - which is redundant as the database
   * does not support this currently, but Nick wanted to keep the option in the API for these to be
   * different in future if required (will require a change to the database and either a different
   * DB call, or filtering of this list).
   *
   * @param dimension
   * @param tool
   * @return
   */
  private List<CriterionDefinition> getCriteriaForDimension(final DimensionEntity dimension,
      final AssessmentTool tool) {

    List<CriterionDefinition> criteria = new ArrayList<>();

    var options = dimension.getValidValues().stream().map(DimensionValidValue::getValueName)
        .collect(Collectors.toList());

    tool.getAssessmentSubmissionTypes().stream().forEach(st -> {
      var criterion = new CriterionDefinition();
      criterion.setCriterionId(st.getSubmissionType().getCode());
      criterion.setName(st.getSubmissionType().getName());
      criterion
          .setType(DimensionSelectionType.fromValue(dimension.getSelectionType().toLowerCase()));
      criterion.setOptions(options);
      criteria.add(criterion);
    });

    return criteria;
  }
}
