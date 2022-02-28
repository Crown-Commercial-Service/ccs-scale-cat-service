package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.lang.String.format;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.createTimestamps;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.updateTimestamps;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import javax.validation.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
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
  private static final String ERR_MSG_FMT_DIMENSION_ID_NOT_SUPPLIED =
      "You must supply a value for dimension-id";
  private static final String ERR_MSG_FMT_DIMENSION_ID_NOT_MATCH_NAME =
      "Dimension name provided [%s] does not match actual dimension name for id [%d] - expected [%s]";
  private static final String ERR_MSG_FMT_DIMENSION_ID_NOT_MATCH_ID =
      "Dimension-id [%d] does not match dimension-id [%d]";
  private static final String ERR_FMT_SUBMISSION_TYPE_NOT_FOUND =
      "Submission Type for Criterion [%s] not found";
  private static final String ERR_FMT_DIMENSION_SELECTION_TYPE_NOT_FOUND =
      "Dimension Select Type [%s] not found";
  private static final String ERR_FMT_EVENT_TYPE_INVALID_FOR_CA_LOT =
      "Assessment event type [%s] invalid for CA [%s], Lot [%s]";

  private final ConclaveService conclaveService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final AgreementsService agreementsService;
  private final AssessmentCalculationService assessmentCalculationService;

  /**
   * Get the Dimensions for an Assessment Tool.
   *
   * @param toolId internal database Assessment Tool id
   * @return
   */
  @Transactional
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

      // Build WeightingRange
      var wr = new WeightingRange();
      wr.setMin(d.getMinWeightingPercentage().intValue());
      wr.setMax(d.getMaxWeightingPercentage().intValue());
      dd.setWeightingRange(wr);

      // Build Options
      List<DimensionOption> dimensionOptions = new ArrayList<>();
      d.getAssessmentTaxons().stream()
          .forEach(at -> dimensionOptions.addAll(recurseAssessmentTaxons(at)));
      dd.setOptions(dimensionOptions);

      // Build Evaluation Criteria
      dd.setEvaluationCriteria(getDimensionCriteria(d));

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
  @Transactional
  public Integer createEmptyAssessment(final String caNumber, final String lotNumber,
      final DefineEventType eventType, final String principal) {

    var lotEventType = agreementsService.getLotEventTypes(caNumber, lotNumber).stream()
        .filter(let -> eventType.name().equals(let.getType())).findFirst()
        .orElseThrow(() -> new ValidationException(
            format(ERR_FMT_EVENT_TYPE_INVALID_FOR_CA_LOT, eventType, caNumber, lotNumber)));

    var assessment = new Assessment().externalToolId(lotEventType.getAssessmentToolId());
    log.debug("Empty assessment created with ext tool-id [{}] for event type [{}]",
        lotEventType.getAssessmentToolId(), eventType);
    return createAssessment(assessment, principal);
  }

  /**
   * Create an Assessment.
   *
   * DimensionRequirements are optional at the create stage, they can be added later using the
   * {@link #updateDimension} method.
   *
   * @param assessment
   * @param principal
   * @return
   */
  @Transactional
  public Integer createAssessment(final Assessment assessment, final String principal) {

    log.debug("Create Assessment");

    var tool = retryableTendersDBDelegate
        .findAssessmentToolByExternalToolId(assessment.getExternalToolId())
        .orElseThrow(() -> new ResourceNotFoundException(
            format(ERR_FMT_TOOL_NOT_FOUND, assessment.getExternalToolId())));

    var conclaveUser = conclaveService.getUserProfile(principal).orElseThrow(
        () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, principal)));

    // Create an AssessmentEntity
    var entity = new AssessmentEntity();
    entity.setTool(tool);
    entity.setStatus(AssessmentStatusEntity.ACTIVE);
    entity.setBuyerOrganisationId(conclaveUser.getOrganisationId());
    entity.setTimestamps(createTimestamps(principal));

    Set<AssessmentSelection> assessmentSelections = new HashSet<>();

    // Optionally also add AssessmentDimensionWeightings and AssessmentSelections
    Set<AssessmentDimensionWeighting> dimensionWeightings =
        Optional.ofNullable(assessment.getDimensionRequirements()).orElse(Collections.emptyList())
            .stream().map(dreq -> {

              // Get Dimension and perform some validation
              if (dreq.getDimensionId() == null) {
                throw new ValidationException(ERR_MSG_FMT_DIMENSION_ID_NOT_SUPPLIED);
              }

              var dimension = validateDimension(dreq, dreq.getDimensionId());

              // Build AssessmentDimensionWeighting
              var dimensionWeighting =
                  buildAssessmentDimensionWeighting(entity, dimension, dreq, principal);

              // Build AssessmentSelections and AssessmentSelectionDetails
              if (dreq.getRequirements() != null) {
                assessmentSelections.addAll(dreq.getRequirements().stream().map(req -> {
                  var assessmentSelection =
                      buildAssessmentSelection(entity, dimension, req, principal);
                  populateAssessmentSelectionDetails(assessmentSelection, req, principal);
                  return assessmentSelection;
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
  @Transactional
  public List<AssessmentSummary> getAssessmentsForUser(final String principal) {
    var assessments = retryableTendersDBDelegate.findAssessmentsForUser(principal);

    return assessments.stream().map(a -> {
      var summary = new AssessmentSummary();
      summary.setAssessmentId(a.getId());
      summary.setExternalToolId(a.getTool().getExternalToolId());
      summary.setStatus(AssessmentStatus.fromValue(a.getStatus().toString().toLowerCase()));
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
  @Transactional
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
      req.setWeighting(dw.getWeightingPercentage().intValue());

      // Build Criteria for Dimension (includedCriteria)
      req.setIncludedCriteria(getAssessmentDimensionCriteria(dw));

      // Build Requirements
      var requirements = assessment.getAssessmentSelections().stream()
          .filter(s -> s.getDimension().getName().equals(dw.getDimension().getName())).map(s -> {
            var requirement = new Requirement();
            requirement.setName(s.getRequirementTaxon().getRequirement().getName());
            requirement.setWeighting(s.getWeightingPercentage().intValue());
            requirement.setRequirementId(s.getRequirementTaxon().getRequirement().getId());

            // Build Criteria for Requirement (values)
            requirement.setValues(s.getAssessmentSelectionDetails().stream().map(asd -> {
              var criterion = new Criterion();
              criterion
                  .setCriterionId(asd.getDimensionSubmissionType().getSubmissionType().getCode());
              criterion.setName(asd.getDimensionSubmissionType().getSubmissionType().getName());

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

    // TODO: Calculate Scores - Nick suggested there may be a db flag to enable/disable this
    var scores = assessmentCalculationService.calculateSupplierScores(assessment, principal);

    var response = new Assessment();
    response.setExternalToolId(assessment.getTool().getExternalToolId());
    response.setAssessmentId(assessmentId);
    response.setDimensionRequirements(dimensions);
    response.setScores(scores);
    response.setStatus(AssessmentStatus.fromValue(assessment.getStatus().toString().toLowerCase()));

    return response;
  }

  /**
   * Update/Add an {@link AssessmentDimensionWeighting} in the database based on a
   * {@link DimensionRequirement} supplied by the API.
   *
   * Every Assessment has a fixed number of Dimensions allowable based on the AssessmentToolId. Not
   * all may be populated when the assessment is created. This method handles updating an existing
   * one, or creating a new record for it if it doesn't exist yet (with the supplied values).
   *
   * Currently - this will overwrite any Requirements supplied, but leave any existing ones intact.
   * Whether it should remove any requirements not supplied has not been specified, so leaving with
   * this behaviour for now - can change if need be.
   *
   * @param assessmentId
   * @param dimensionId
   * @param dimensionRequirement
   * @param principal
   * @return
   */
  @Transactional
  public Integer updateDimension(final Integer assessmentId, final Integer dimensionId,
      final DimensionRequirement dimensionRequirement, final String principal) {

    log.debug("Update Dimension " + dimensionId);

    var assessment = retryableTendersDBDelegate.findAssessmentById(assessmentId).orElseThrow(
        () -> new ResourceNotFoundException(format(ERR_FMT_ASSESSMENT_NOT_FOUND, assessmentId)));

    var response = assessment.getDimensionWeightings().stream()
        .filter(dw -> dw.getDimension().getId().equals(dimensionId)).findFirst();

    AssessmentDimensionWeighting dimensionWeighting;

    // Validates input whether we are creating a new DimensionWeighting below or not
    var dimension = validateDimension(dimensionRequirement, dimensionId);

    if (response.isPresent()) {
      dimensionWeighting = response.get();
      if (dimensionRequirement.getWeighting() == null) {
        dimensionWeighting.setWeightingPercentage(BigDecimal.ZERO);
      } else {
        dimensionWeighting
            .setWeightingPercentage(new BigDecimal(dimensionRequirement.getWeighting()));
      }
      dimensionWeighting
          .setTimestamps(updateTimestamps(dimensionWeighting.getTimestamps(), principal));
      dimensionWeighting.setDimensionSubmissionTypes(
          buildDimensionSubmissionTypes(dimensionRequirement, dimension));
    } else {
      log.debug("Could not find existing Dimension Weighting for Assessment " + assessmentId
          + ", Dimension " + dimensionId + " - so creating one");
      dimensionWeighting =
          buildAssessmentDimensionWeighting(assessment, dimension, dimensionRequirement, principal);
    }

    // Verify the total weightings of all dimensions <= 100
    var totalDimensionWeightings = assessment.getDimensionWeightings().stream()
        .map(AssessmentDimensionWeighting::getWeightingPercentage)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalDimensionWeightings.intValue() > 100) {
      throw new ValidationException("Sum of all Dimension Weightings cannot exceed 100%");
    }

    if (Boolean.TRUE.equals(dimensionRequirement.getOverwriteRequirements())) {
      assessment.getAssessmentSelections().forEach(as -> {
        var match = dimensionRequirement.getRequirements().stream().filter(
            dr -> dr.getRequirementId().equals(as.getRequirementTaxon().getRequirement().getId()))
            .findAny();
        if (match.isEmpty()) {
          log.debug(">>>> REMOVE ASSESSMENT SELECTION: " + as.getId() + ", REQ: "
              + +as.getRequirementTaxon().getRequirement().getId());
          retryableTendersDBDelegate.delete(as);
        }
      });

    }

    retryableTendersDBDelegate.save(dimensionWeighting);

    if (dimensionRequirement.getRequirements() != null) {
      dimensionRequirement.getRequirements().stream()
          .forEach(r -> updateRequirement(assessmentId, dimensionId, r, principal));
    }

    return dimensionId;
  }

  /**
   * Update/Add an {@link AssessmentSelection} in the database based on a {@link Requirement}
   * supplied by the API.
   *
   * Every Assessment has a fixed number of Dimensions & Requirements allowable based on the
   * AssessmentToolId. Not all may be populated when the assessment is created. This method handles
   * updating an existing one, or creating a new record for it if it doesn't exist yet (with the
   * supplied values).
   *
   * Currently this will overwrite the existing Requirement and Values.
   *
   * @param assessmentId
   * @param dimensionId
   * @param requirement
   * @param principal
   * @return
   */
  @Transactional
  public Integer updateRequirement(final Integer assessmentId, final Integer dimensionId,
      final Requirement requirement, final String principal) {

    log.debug("Update requirement " + requirement.getRequirementId() + " for dimension "
        + dimensionId + " on assessment " + assessmentId);

    var assessment = retryableTendersDBDelegate.findAssessmentById(assessmentId).orElseThrow(
        () -> new ResourceNotFoundException(format(ERR_FMT_ASSESSMENT_NOT_FOUND, assessmentId)));

    var dimension = retryableTendersDBDelegate.findDimensionById(dimensionId)
        .orElseThrow(() -> new ResourceNotFoundException(
            String.format(ERR_FMT_DIMENSION_NOT_FOUND, dimensionId)));

    // Create the AssessmentSelection for the Dimension/Requirement/Assessment if it doesn't exist
    AssessmentSelection selection;
    var response = assessment.getAssessmentSelections().stream()
        .filter(s -> s.getDimension().getId().equals(dimensionId) && s.getRequirementTaxon()
            .getRequirement().getId().equals(requirement.getRequirementId()))
        .findFirst();

    if (response.isPresent()) {
      selection = response.get();
      if (requirement.getWeighting() == null) {
        selection.setWeightingPercentage(BigDecimal.ZERO);
      } else {
        selection.setWeightingPercentage(new BigDecimal(requirement.getWeighting()));
      }
      selection.setTimestamps(updateTimestamps(selection.getTimestamps(), principal));
    } else {
      log.debug("Could not find existing Assessment Selection for Assessment " + assessmentId
          + ", Dimension " + dimensionId + " and Requirement " + requirement.getRequirementId()
          + " - so creating one");
      selection = buildAssessmentSelection(assessment, dimension, requirement, principal);
    }

    // Add AssessmentSelectionDetails to AssessmentSelection
    populateAssessmentSelectionDetails(selection, requirement, principal);

    retryableTendersDBDelegate.save(selection);

    return requirement.getRequirementId();
  }

  /**
   * Retrieve a {@link DimensionEntity} and perform some validation on the request to catch
   * scenarios where the caller has provided superfluous information that doesn't match up (e.g.
   * provided a dimension name that doesn't match the id they provided). We only use IDs but this
   * may help to catch user errors.
   *
   * @param dimensionRequirement
   * @param dimensionId if they have provided an ID separately to the DimensionRequirement, e.g. as
   *        a path parameter
   * @return
   */
  private DimensionEntity validateDimension(final DimensionRequirement dimensionRequirement,
      final Integer dimensionId) {

    if (dimensionRequirement.getDimensionId() != null
        && !dimensionRequirement.getDimensionId().equals(dimensionId)) {
      throw new ValidationException(format(ERR_MSG_FMT_DIMENSION_ID_NOT_MATCH_ID,
          dimensionRequirement.getDimensionId(), dimensionId));
    }

    var dimension = retryableTendersDBDelegate.findDimensionById(dimensionId)
        .orElseThrow(() -> new ResourceNotFoundException(
            String.format(ERR_FMT_DIMENSION_NOT_FOUND, dimensionId)));

    if (dimensionRequirement.getName() != null
        && !dimensionRequirement.getName().equals(dimension.getName())) {
      throw new ValidationException(format(ERR_MSG_FMT_DIMENSION_ID_NOT_MATCH_NAME,
          dimensionRequirement.getName(), dimensionId, dimension.getName()));
    }

    return dimension;
  }

  /**
   * Build an AssessmentDimensionWeighting (does not persist).
   *
   * @param assessment
   * @param dimension
   * @param dimensionRequirement
   * @param principal
   * @return
   */
  private AssessmentDimensionWeighting buildAssessmentDimensionWeighting(
      final AssessmentEntity assessment, final DimensionEntity dimension,
      final DimensionRequirement dimensionRequirement, final String principal) {
    var dimensionWeighting = new AssessmentDimensionWeighting();
    dimensionWeighting.setAssessment(assessment);
    dimensionWeighting.setDimension(dimension);
    dimensionWeighting.setWeightingPercentage(new BigDecimal(dimensionRequirement.getWeighting()));
    dimensionWeighting.setTimestamps(createTimestamps(principal));
    dimensionWeighting.setDimensionSubmissionTypes(
        buildDimensionSubmissionTypes(dimensionRequirement, dimension));
    return dimensionWeighting;
  }

  /**
   * Build a list of {@link DimensionSubmissionType} based on the includedCriteria submitted.
   *
   * @param dimensionRequirement
   * @param dimension
   * @return
   */
  private Set<DimensionSubmissionType> buildDimensionSubmissionTypes(
      final DimensionRequirement dimensionRequirement, final DimensionEntity dimension) {

    var dimensionSubmissionTypes = dimension.getDimensionSubmissionTypes();
    var validToolSubmissionTypeIDs =
        dimensionSubmissionTypes.stream().map(DimensionSubmissionType::getSubmissionType)
            .map(SubmissionType::getCode).collect(Collectors.toSet());

    return dimensionRequirement.getIncludedCriteria().stream().map(cd -> {
      if (!validToolSubmissionTypeIDs.contains(cd.getCriterionId())) {
        throw new ValidationException(
            format(ERR_FMT_SUBMISSION_TYPE_NOT_FOUND, cd.getCriterionId()));
      }
      return dimensionSubmissionTypes.stream()
          .filter(tst -> Objects.equals(tst.getSubmissionType().getCode(), cd.getCriterionId()))
          .findFirst().get();
    }).collect(Collectors.toSet());
  }

  /**
   * Build an AssessmentSelection, excluding AssessmentSelectionDetails (does not persist).
   *
   * @param assessment
   * @param dimension
   * @param requirement
   * @param principal
   * @return
   */
  private AssessmentSelection buildAssessmentSelection(final AssessmentEntity assessment,
      final DimensionEntity dimension, final Requirement requirement, final String principal) {

    var tool = assessment.getTool();

    var requirementTaxon = retryableTendersDBDelegate
        .findRequirementTaxon(requirement.getRequirementId(), tool.getId())
        .orElseThrow(() -> new ResourceNotFoundException(format(ERR_FMT_REQUIREMENT_TAXON_NOT_FOUND,
            requirement.getRequirementId(), tool.getId())));

    var as = new AssessmentSelection();
    as.setAssessment(assessment);
    as.setDimension(dimension);
    as.setWeightingPercentage(new BigDecimal(requirement.getWeighting()));
    as.setTimestamps(createTimestamps(principal));
    as.setRequirementTaxon(requirementTaxon);

    return as;
  }

  /**
   * Given an AssessmentSelection, add AssessmentSelectionDetails to it (does not persist).
   *
   * This is separate from {@link #buildAssessmentSelection()} as we that creates a new
   * AssessmentSelection and we may want to update the AssessmentSelectionDetails on an existing
   * one.
   *
   * @param selection
   * @param requirement
   * @param principal
   * @return
   */
  private AssessmentSelection populateAssessmentSelectionDetails(
      final AssessmentSelection selection, final Requirement requirement, final String principal) {

    log.debug("populateAssessmentSelectionDetails for selection " + selection.getId()
        + " and requirement " + requirement.getName());

    var dimension = selection.getDimension();

    // Build AssessmentSelectionDetails
    if (requirement.getValues() != null) {
      selection.setAssessmentSelectionDetails(requirement.getValues().stream().map(c -> {
        var asd = new AssessmentSelectionDetail();
        asd.setAssessmentSelection(selection);
        var dimensionSubmissionType = dimension.getDimensionSubmissionTypes().stream()
            .filter(ast -> ast.getSubmissionType().getCode().equals(c.getCriterionId())).findFirst()
            .orElseThrow(() -> new ValidationException(
                format(ERR_FMT_SUBMISSION_TYPE_NOT_FOUND, c.getCriterionId())));
        asd.setDimensionSubmissionType(dimensionSubmissionType);
        asd.setTimestamps(createTimestamps(principal));

        var criteriaSelectionType = CriteriaSelectionType
            .fromValue(dimensionSubmissionType.getSelectionType().toLowerCase());

        switch (criteriaSelectionType) {
          case SELECT:
          case MULTISELECT:
            var validValue = getValidValueByName(dimension, c.getValue());
            asd.setRequirementValidValueCode(validValue.getKey().getValueCode());
            break;
          case INTEGER:
            asd.setRequirementValue(new BigDecimal(c.getValue()));
            break;
          default:
            throw new ValidationException(
                format(ERR_FMT_DIMENSION_SELECTION_TYPE_NOT_FOUND, criteriaSelectionType));
        }

        log.debug("Built assessmentSelectionDetail " + asd.getRequirementValidValueCode()
            + ", for criterion " + asd.getDimensionSubmissionType().getSubmissionType().getCode()
            + " and selection " + asd.getAssessmentSelection().getId());

        return asd;
      }).collect(Collectors.toSet()));
    }

    return selection;
  }

  /**
   * Recurses down the {@link AssessmentTaxon} and {@link RequirementTaxon} tree of objects building
   * a tree of {@link DimensionOption} objects.
   *
   * @param assessmentTaxon
   * @return
   */
  private List<DimensionOption> recurseAssessmentTaxons(final AssessmentTaxon assessmentTaxon) {

    log.debug("Assessment Taxon :" + assessmentTaxon.getName());
    List<DimensionOption> dimensionOptions =
        new ArrayList<>(assessmentTaxon.getRequirementTaxons().stream().map(rt -> {

          log.debug(" - requirement :" + rt.getRequirement().getName());
          var rtOption = new DimensionOption();
          rtOption.setName(rt.getRequirement().getName());
          rtOption.setRequirementId(rt.getRequirement().getId());
          rtOption.setGroupRequirement(rt.getRequirement().getGroupRequirement());

          // If it is a group requirement - no need to duplicate in the `groups` collection
          if (Boolean.TRUE.equals(rt.getRequirement().getGroupRequirement())) {
            rtOption.setGroups(recurseUpTree(assessmentTaxon.getParentTaxon(), new ArrayList<>()));
          } else {
            rtOption.setGroups(recurseUpTree(assessmentTaxon, new ArrayList<>()));
          }
          return rtOption;

        }).collect(Collectors.toList()));

    // Recurse down child Assessment Taxon collection
    if (!assessmentTaxon.getAssessmentTaxons().isEmpty()) {
      log.debug("Assessment Taxon : process children..");
      assessmentTaxon.getAssessmentTaxons().stream()
          .forEach(at -> dimensionOptions.addAll(recurseAssessmentTaxons(at)));
    }

    return dimensionOptions;
  }

  /**
   * Given an {@link AssessmentTaxon}, will recurse up the parents to build a hierarchy of
   * {@link DimensionOptionGroups} objects. The highest group in the hierarchy will be level 1, the
   * second level 2, and so on..
   *
   * @param assessmentTaxon
   * @param optionGroups
   * @return
   */
  private List<DimensionOptionGroups> recurseUpTree(final AssessmentTaxon assessmentTaxon,
      final List<DimensionOptionGroups> optionGroups) {

    log.debug("  - traverse up taxon tree :" + assessmentTaxon.getName());
    var rtOptionGroup = new DimensionOptionGroups();
    rtOptionGroup.setName(assessmentTaxon.getName());
    optionGroups.add(rtOptionGroup);

    // Recurse
    if (assessmentTaxon.getParentTaxon() != null) {
      return recurseUpTree(assessmentTaxon.getParentTaxon(), optionGroups);
    }

    // Remove the highest AssessmentTaxon element from the list - as this is the taxon name and not
    // required to be shown. Reverse the list and add the levels.
    Collections.reverse(optionGroups);
    optionGroups.remove(0);
    var level = 1;
    for (DimensionOptionGroups og : optionGroups) {
      og.setLevel(level++);
    }

    return optionGroups;
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
   * Get the Criteria for an Assessment Tool Dimension.
   *
   * This returns the valid criterion (aka submission types) for a Dimension per assessment tool.
   * I.e. it should be used to inform the client of the available valid submission types per
   * Dimension, for them to select from when creating / updating an assessment.
   *
   * @param dimension
   * @param tool
   * @return
   */
  private List<CriterionDefinition> getDimensionCriteria(final DimensionEntity dimension) {

    return buildCriteria(dimension, dimension.getDimensionSubmissionTypes());
  }

  /**
   * Gets the (buyer supplied) Criteria for an Assessment Dimension.
   *
   * Contrasts with {@link #getAssessmentToolDimensionCriteria(DimensionEntity, AssessmentTool)} as
   * this builds the criteria via the join between assessment dimensions and submission types (not
   * just via assessment tool)
   *
   * @param assessmentDimensionWeighting
   * @return
   */
  private List<CriterionDefinition> getAssessmentDimensionCriteria(
      final AssessmentDimensionWeighting assessmentDimensionWeighting) {

    return buildCriteria(assessmentDimensionWeighting.getDimension(),
        assessmentDimensionWeighting.getDimensionSubmissionTypes());
  }

  private List<CriterionDefinition> buildCriteria(final DimensionEntity dimension,
      final Collection<DimensionSubmissionType> dimensionSubmissionTypes) {
    List<CriterionDefinition> criteria = new ArrayList<>();

    var options = dimension.getValidValues().stream().map(DimensionValidValue::getValueName)
        .collect(Collectors.toList());

    dimensionSubmissionTypes.stream().forEach(st -> {
      var criterion = new CriterionDefinition();
      var selectionType = CriteriaSelectionType.fromValue(st.getSelectionType().toLowerCase());
      criterion.setCriterionId(st.getSubmissionType().getCode());
      criterion.setName(st.getSubmissionType().getName());
      criterion.setType(selectionType);
      if (CriteriaSelectionType.SELECT == selectionType
          || CriteriaSelectionType.MULTISELECT == selectionType) {
        criterion.setOptions(options);
      }
      criteria.add(criterion);
    });

    return criteria;
  }

}
