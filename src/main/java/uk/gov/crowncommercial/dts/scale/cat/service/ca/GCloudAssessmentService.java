package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.NotSupportedException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.GCloudAssessmentSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.AssessmentStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.GCloudAssessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.GCloudResult;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentStatusEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudAssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudAssessmentResult;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;

import java.net.URI;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.createTimestamps;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.updateTimestamps;

@Service
@RequiredArgsConstructor
@Slf4j
public class GCloudAssessmentService {
    private static final String ERR_MSG_FMT_ASSESSMENT_RESULTS_NOT_FOUND = "Assessment [%s] results not found";
    private static final String ERR_MSG_FMT_CONCLAVE_USER_MISSING = "User [%s] not found in Conclave";
    private static final String ERR_MSG_FMT_ASSESSMENT_NOT_FOUND = "Assessment [%s] not found";
    private static final String ERR_MSG_FMT_CANNOT_DELETE_ASSESSMENT = "Cannot delete completed assessment [%s]";
    private static final String ERR_MSG_FMT_INVALID_EXTERNAL_TOOL_ID = "External Tool Id [%s] is not valid for Gcloud Assessment operations";
    private static final String TOOL_NAME_GCLOUD = "GCloud 13 Search";

    private static final String TIMEZONE_NAME = "Europe/London";

    private final ConclaveService conclaveService;
    private final RetryableTendersDBDelegate retryableTendersDBDelegate;

    /**
     * Create a GCloud Assessment.
     *
     * @param assessment
     * @param principal
     * @return
     */
    @Transactional
    public Integer createGcloudAssessment(final GCloudAssessment assessment, final String principal) {
        log.debug("Creating GCloud assessment");

        var conclaveUser = conclaveService.getUserProfile(principal).orElseThrow(
                () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, principal)));

        // Make sure the object we've been passed contains the GCloud ExternalToolId before proceeding
        if (!isExternalToolIdValidForGcloud(assessment.getExternalToolId())) {
            throw new NotSupportedException(String.format(ERR_MSG_FMT_INVALID_EXTERNAL_TOOL_ID, assessment.getExternalToolId()));
        }

        // Now create a GCloudAssessmentEntity and populate it from our input
        GCloudAssessmentEntity assessmentEntity = new GCloudAssessmentEntity();

        assessmentEntity.setAssessmentName(assessment.getAssessmentName());
        assessmentEntity.setStatus(AssessmentStatusEntity.ACTIVE); // Map this to active per the createAssessment function above
        assessmentEntity.setDimensionRequirements(assessment.getDimensionRequirements());
        assessmentEntity.setResultsSummary(assessment.getResultsSummary());
        assessmentEntity.setTimestamps(createTimestamps(principal));
        assessmentEntity.setExternalToolId(Integer.parseInt(assessment.getExternalToolId()));

        // Save our assessment entity
        Integer saveResult = retryableTendersDBDelegate.save(assessmentEntity).getId();

        if (saveResult > 0) {
            // Now setup any assessment results we have, if any
            Set<GCloudAssessmentResult> results = Optional.ofNullable(assessment.getResults()).orElse(Collections.emptyList()).stream().map(result -> {
                // For each result we have, map it to a GCloudAssessmentResult and save it into the database
                GCloudAssessmentResult resultEntity = new GCloudAssessmentResult();

                resultEntity.setAssessmentId(saveResult);
                resultEntity.setServiceName(result.getServiceName());
                resultEntity.setSupplierName(result.getSupplier().getName());
                resultEntity.setServiceDescription(result.getServiceDescription());
                resultEntity.setServiceLink(result.getServiceLink().toString());
                resultEntity.setTimestamps(createTimestamps(principal));

                return resultEntity;
            }).collect(Collectors.toSet());

            if (CollectionUtils.isNotEmpty(results)) {
                 retryableTendersDBDelegate.saveAll(results);
            }
        }

        return saveResult;
    }

    /**
     * Update a GCloud Assessment.
     *
     * @param assessment
     * @param assessmentId
     * @param principal
     * @return
     */
    @Transactional
    public void updateGcloudAssessment(final GCloudAssessment assessment, final Integer assessmentId, final String principal) {
        log.debug("Updating Gcloud assessment " + assessmentId);

        // First of all, retrieve the existing assessment from the database, to make sure it exists
        GCloudAssessmentEntity model = retryableTendersDBDelegate.findGcloudAssessmentById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        format(ERR_MSG_FMT_ASSESSMENT_NOT_FOUND, assessmentId)));

        // Make sure the object we've been passed contains the GCloud ExternalToolId before proceeding
        if (!isExternalToolIdValidForGcloud(assessment.getExternalToolId())) {
            throw new NotSupportedException(String.format(ERR_MSG_FMT_INVALID_EXTERNAL_TOOL_ID, assessment.getExternalToolId()));
        }

        if (model != null) {
            // We now know the assessment does exist, so now we can proceed to update it
            model.setId(assessmentId);
            model.setAssessmentName(assessment.getAssessmentName());

            if (assessment.getStatus() == AssessmentStatus.ACTIVE) {
                model.setStatus(AssessmentStatusEntity.ACTIVE);
            } else {
                model.setStatus(AssessmentStatusEntity.COMPLETE);
            }

            model.setExternalToolId(Integer.parseInt(assessment.getExternalToolId()));
            model.setDimensionRequirements(assessment.getDimensionRequirements());
            model.setResultsSummary(assessment.getResultsSummary());
            model.setTimestamps(updateTimestamps(model.getTimestamps(), principal));

            // We do not map results here - we need to now update the core record, then clear down results and re-create them fresh
            retryableTendersDBDelegate.save(model);

            // Now we first need to clear down all the existing results for this assessment in the DB
            retryableTendersDBDelegate.deleteGcloudAssessmentResultsById(assessmentId);

            // Finally we can now spool up our results and re-save them
            Set<GCloudAssessmentResult> gCloudAssessmentResultSet= Optional.ofNullable(assessment.getResults()).orElse(Collections.emptyList()).stream().map(result->{
                GCloudAssessmentResult resultEntity = new GCloudAssessmentResult();

                resultEntity.setAssessmentId(assessmentId);
                resultEntity.setServiceName(result.getServiceName());
                resultEntity.setSupplierName(result.getSupplier().getName());
                resultEntity.setServiceDescription(result.getServiceDescription());
                resultEntity.setServiceLink(result.getServiceLink().toString());
                resultEntity.setTimestamps(createTimestamps(principal));

                return resultEntity;
            }).collect(Collectors.toSet());


            if (CollectionUtils.isNotEmpty(gCloudAssessmentResultSet)) {
                retryableTendersDBDelegate.saveAll(gCloudAssessmentResultSet);
            }
        }
    }

    /**
     * Gets a summary model for a GCloud Assessment
     */
    @Transactional
    public GCloudAssessmentSummary getGcloudAssessmentSummary(final Integer assessmentId) {
        // Grab the assessment details from the DB
        GCloudAssessmentEntity assessmentModel = retryableTendersDBDelegate.findGcloudAssessmentById(assessmentId).orElse(null);

        if (assessmentModel != null) {
            // We appear to have data, so map it to our desired output model
            GCloudAssessmentSummary model = new GCloudAssessmentSummary();

            model.id = assessmentModel.getId();
            model.resultsSummary = assessmentModel.getResultsSummary();
            model.dimensionRequirements = assessmentModel.getDimensionRequirements();
            model.assessmentName = assessmentModel.getAssessmentName();
            model.status = AssessmentStatus.fromValue(assessmentModel.getStatus().toString().toLowerCase());

            Timestamps timestamps = assessmentModel.getTimestamps();
            if (timestamps.getUpdatedAt() != null) {
                model.lastUpdate = OffsetDateTime.ofInstant(timestamps.getUpdatedAt(), ZoneId.of(TIMEZONE_NAME));
            } else {
                model.lastUpdate = OffsetDateTime.ofInstant(timestamps.getCreatedAt(), ZoneId.of(TIMEZONE_NAME));
            }

            return model;
        }

        // If we've got this far, there's been an issue or no data was found - return null
        return null;
    }

    /**
     * Get GCloud Assessment details.
     *
     * @param assessmentId
     * @return
     */
    @Transactional
    public GCloudAssessment getGcloudAssessment(final Integer assessmentId) {
        GCloudAssessmentEntity assessment = retryableTendersDBDelegate.findGcloudAssessmentById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        format(ERR_MSG_FMT_ASSESSMENT_NOT_FOUND, assessmentId)));

        // Now build the results
        Set<GCloudAssessmentResult> results = retryableTendersDBDelegate.findGcloudResultsByAssessmentId(assessmentId);

        ArrayList<GCloudResult> resultsList = new ArrayList<>();

        for (GCloudAssessmentResult result : results) {
            GCloudResult resultModel = new GCloudResult();
            resultModel.setServiceName(result.getServiceName());
            resultModel.setServiceDescription(result.getServiceDescription());
            resultModel.setServiceLink(URI.create(result.getServiceLink()));

            Supplier supplierModel = new Supplier();
            supplierModel.setName(result.getSupplierName());
            resultModel.setSupplier(supplierModel);

            resultsList.add(resultModel);
        }

        // Now map everything to our return model and output
        GCloudAssessment responseModel = new GCloudAssessment();
        responseModel.setAssessmentId(assessment.getId());
        responseModel.setAssessmentName(assessment.getAssessmentName());
        responseModel.setExternalToolId(assessment.getExternalToolId().toString());
        responseModel.setStatus(AssessmentStatus.fromValue(assessment.getStatus().toString().toLowerCase()));
        responseModel.setDimensionRequirements(assessment.getDimensionRequirements());
        responseModel.setResultsSummary(assessment.getResultsSummary());
        responseModel.setResults(resultsList);

        Timestamps timestamps = assessment.getTimestamps();

        if (timestamps.getUpdatedAt() != null) {
            responseModel.setLastUpdate(OffsetDateTime.ofInstant(timestamps.getUpdatedAt(), ZoneId.of(TIMEZONE_NAME)));
        } else {
            responseModel.setLastUpdate(OffsetDateTime.ofInstant(timestamps.getCreatedAt(), ZoneId.of(TIMEZONE_NAME)));
        }

        return responseModel;
    }

    /**
     * Delete a GCloud Assessment.
     *
     * @param assessmentId
     * @return
     */
    @Transactional
    public void deleteGcloudAssessment(final Integer assessmentId) {
        log.debug("Deleting Gcloud assessment " + assessmentId);

        // First of all, retrieve the existing assessment from the database - we need to check its state before just deleting it
        GCloudAssessmentEntity model = retryableTendersDBDelegate.findGcloudAssessmentById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        format(ERR_MSG_FMT_ASSESSMENT_NOT_FOUND, assessmentId)));

        if (model != null) {
            // We found the assessment - now check its status, we only proceed if it's still active
            if (Objects.equals(model.getStatus().toString().toLowerCase(), AssessmentStatus.ACTIVE.toString().toLowerCase())) {
                // Assessment is active, so we are able to delete it.  We need to delete the assessment itself and any results attached
                retryableTendersDBDelegate.deleteGcloudAssessmentById(assessmentId);
            } else {
                // Assessment is not active - we can't delete this.  Throw an error instead
                throw(new AuthorisationFailureException(format(ERR_MSG_FMT_CANNOT_DELETE_ASSESSMENT, assessmentId)));
            }
        }
    }

    /**
     * Check whether the provided External Tool ID matches the stored GCloud Assessment Tool ID
     *
     * @param externalToolId
     * @return
     */

    @Cacheable(value = "isExternalToolIdValidForGcloud",  key = "{#externalToolId}")
    protected boolean isExternalToolIdValidForGcloud(final String externalToolId) {
        // Always assume the ID isn't valid until the DB tells us otherwise
        boolean isValid = false;

        // Now we need to check that the ID provided exists as a Tool, and matches the GCloud Tool ID
        Optional<AssessmentTool> optionalTool = retryableTendersDBDelegate.findAssessmentToolByExternalToolId(externalToolId);

        if (optionalTool.isPresent()) {
            AssessmentTool matchingTool = optionalTool.get();

            if (Objects.equals(matchingTool.getName(), TOOL_NAME_GCLOUD)) {
                // Looks like a tool exists for this ID, and it's the Gcloud tool, so this is valid
                isValid = true;
            }
        }

        return isValid;
    }
}
