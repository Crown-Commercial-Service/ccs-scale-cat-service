package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.TendersRetryable;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.readonly.CalculationBaseRepo;

/**
 * Simple retrying delegate to JPA repos {@link ProcurementProjectRepo}
 */
@Service
@RequiredArgsConstructor
public class RetryableTendersDBDelegate {

  private final ProcurementProjectRepo procurementProjectRepo;
  private final ProcurementEventRepo procurementEventRepo;
  private final OrganisationMappingRepo organisationMappingRepo;
  private final JourneyRepo journeyRepo;
  private final DocumentTemplateRepo documentTemplateRepo;
  private final AssessmentRepo assessmentRepo;
  private final GCloudAssessmentRepo gcloudAssessmentRepo;
  private final GCloudAssessmentResultRepo gCloudAssessmentResultRepo;
  private final AssessmentToolRepo assessmentToolRepo;
  private final AssessmentDimensionWeightingRepo assessmentDimensionWeightingRepo;
  private final DimensionRepo dimensionRepo;
  private final AssessmentSelectionRepo assessmentSelectionRepo;
  private final RequirementTaxonRepo requirementTaxonRepo;
  private final AssessmentTaxonRepo assessmentTaxonRepo;
  private final CalculationBaseRepo calculationBaseRepo;
  private final AssessmentResultRepo assessmentResultRepo;
  private final ProjectUserMappingRepo projectUserMappingRepo;
  private final SupplierSelectionRepo supplierSelectionRepo;
  private final BuyerUserDetailsRepo buyerUserDetailsRepo;
  private final ContractDetailsRepo contractDetailsRepo;

  @TendersRetryable
  public ProcurementProject save(final ProcurementProject procurementProject) {
    return procurementProjectRepo.saveAndFlush(procurementProject);
  }

  @TendersRetryable
  public ProcurementEvent save(final ProcurementEvent procurementevent) {
    return procurementEventRepo.save(procurementevent);
  }

  @TendersRetryable
  public Optional<ProcurementProject> findProcurementProjectById(final Integer id) {
    return procurementProjectRepo.findById(id);
  }

  @TendersRetryable
  public List<ProcurementProject> findByExternalProjectIdIn(final Set<String> externalProjectIds) {
    return procurementProjectRepo.findByExternalProjectIdIn(externalProjectIds);
  }

  @TendersRetryable
  public Optional<ProcurementEvent> findProcurementEventById(final Integer id) {
    return procurementEventRepo.findById(id);
  }

  @TendersRetryable
  public Optional<ProcurementEvent> findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
      final Integer eventIdKey, final String ocdsAuthorityName, final String ocidPrefix) {
    return procurementEventRepo.findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
        eventIdKey, ocdsAuthorityName, ocidPrefix);
  }

  @TendersRetryable
  public Set<OrganisationMapping> findOrganisationMappingByOrganisationIdIn(
      final Set<String> organisationIds) {
    return organisationMappingRepo.findByOrganisationIdIn(organisationIds);
  }

  @TendersRetryable
  public Optional<OrganisationMapping> findOrganisationMappingByExternalOrganisationId(
      final Integer externalOrganisationId) {
    return organisationMappingRepo.findByExternalOrganisationId(externalOrganisationId);
  }

  @TendersRetryable
  public OrganisationMapping save(final OrganisationMapping organisationMapping) {
    return organisationMappingRepo.saveAndFlush(organisationMapping);
  }

  @TendersRetryable
  public Optional<OrganisationMapping> findOrganisationMappingByOrganisationId(
      final String organisationId) {
    return organisationMappingRepo.findByOrganisationId(organisationId);
  }

  @TendersRetryable
  public JourneyEntity save(final JourneyEntity journey) {
    return journeyRepo.save(journey);
  }

  @TendersRetryable
  public Optional<JourneyEntity> findJourneyByExternalId(final String externalId) {
    return journeyRepo.findByExternalId(externalId);
  }

  @TendersRetryable
  public Optional<DocumentTemplate> findById(final Integer fileId) {
    return documentTemplateRepo.findById(fileId);
  }

  @TendersRetryable
  public Set<DocumentTemplate> findByEventType(final String eventType) {
    return documentTemplateRepo.findByEventType(eventType);
  }

  @TendersRetryable
  public Set<DocumentTemplate> findByEventStage(final String eventStage) {
    return documentTemplateRepo.findByEventStage(eventStage);
  }

  @TendersRetryable
  public Set<ProcurementEvent> findProcurementEventsByProjectId(final Integer projectId) {
    return procurementEventRepo.findByProjectId(projectId);
  }

  @TendersRetryable
  public Set<AssessmentEntity> findAssessmentsForUser(final String userId) {
    return assessmentRepo.findByTimestampsCreatedBy(userId);
  }

  @TendersRetryable
  public Set<GCloudAssessmentEntity> findGcloudAssessmentsForUser(final String userId) {
    return gcloudAssessmentRepo.findByTimestampsCreatedBy(userId);
  }

  @TendersRetryable
  public AssessmentEntity save(final AssessmentEntity assessment) {
    return assessmentRepo.saveAndFlush(assessment);
  }

  @TendersRetryable
  public GCloudAssessmentEntity save(final GCloudAssessmentEntity assessment) {
    return gcloudAssessmentRepo.saveAndFlush(assessment);
  }

  @TendersRetryable
  public Optional<AssessmentEntity> findAssessmentById(final Integer id) {
    return assessmentRepo.findById(id);
  }

  @TendersRetryable
  public Optional<GCloudAssessmentEntity> findGcloudAssessmentById(final Integer id) {
    return gcloudAssessmentRepo.findById(id);
  }

  @TendersRetryable
  public Set<GCloudAssessmentResult> findGcloudResultsByAssessmentId(final Integer id) {
    return gCloudAssessmentResultRepo.findByAssessmentId(id);
  }

  @TendersRetryable
  public AssessmentDimensionWeighting save(
      final AssessmentDimensionWeighting assessmentDimensionWeighting) {
    return assessmentDimensionWeightingRepo.saveAndFlush(assessmentDimensionWeighting);
  }

  @TendersRetryable
  public Optional<AssessmentTool> findAssessmentToolById(final Integer toolId) {
    return assessmentToolRepo.findById(toolId);
  }

  @TendersRetryable
  public Optional<AssessmentTool> findAssessmentToolByExternalToolId(final String externalToolId) {
    return assessmentToolRepo.findByExternalToolId(externalToolId);
  }

  @TendersRetryable
  public Set<DimensionEntity> findDimensionsByToolId(final Integer toolId) {
    return dimensionRepo.findByAssessmentToolsId(toolId);
  }

  @TendersRetryable
  public Set<AssessmentSelection> findByDimensionIdAndRequirementId(final Integer assessmentId,
      final Integer dimensionId, final Integer requirementId) {
    return assessmentSelectionRepo.findByDimensionIdAndRequirementTaxonRequirementId(dimensionId,
        requirementId);
  }

  @TendersRetryable
  public AssessmentSelection save(final AssessmentSelection assessmentSelection) {
    return assessmentSelectionRepo.save(assessmentSelection);
  }

  @TendersRetryable
  public Optional<RequirementTaxon> findRequirementTaxon(final Integer requirementId,
      final Integer toolId) {
    return requirementTaxonRepo.findByRequirementIdAndTaxonSubmissionGroupAssessmentToolsId(requirementId, toolId);
  }

  @TendersRetryable
  public Optional<DimensionEntity> findDimensionById(final Integer dimensionId) {
    return dimensionRepo.findById(dimensionId);
  }

  @TendersRetryable
  public Optional<DimensionEntity> findDimensionByName(final String name) {
    return dimensionRepo.findByName(name);
  }

  @TendersRetryable
  public Optional<AssessmentTaxon> findAssessmentTaxonById(final Integer assessmentTaxonId) {
    return assessmentTaxonRepo.findById(assessmentTaxonId);
  }

  @TendersRetryable
  public Set<AssessmentTaxon> findAssessmentTaxonByToolAndDimension(final Integer assessmentToolId, Integer dimensionId) {
    return assessmentTaxonRepo.findBySubmissionGroupAssessmentToolsIdAndDimensionsId(assessmentToolId, dimensionId);
  }

  @TendersRetryable
  public Set<CalculationBase> findCalculationBaseByDimensionIdAndSuppliers(
      final Integer dimensionId, final List<String> suppliers) {
    return calculationBaseRepo.findByDimensionIdAndSupplierIdIn(dimensionId, suppliers);
  }

  @TendersRetryable
  public Set<CalculationBase> findCalculationBaseByDimensionId(final Integer dimensionId) {
    return calculationBaseRepo.findByDimensionId(dimensionId);
  }

  @TendersRetryable
  public Set<CalculationBase> findCalculationBaseByAssessmentId(final Integer assessmentId) {
    return calculationBaseRepo.findByAssessmentId(assessmentId);
  }

  @TendersRetryable
  public Optional<AssessmentResult> findByAssessmentIdAndSupplierOrganisationId(
      final Integer assessmentId, final String supplierOrganisationId) {
    return assessmentResultRepo.findByAssessmentIdAndSupplierOrganisationId(assessmentId,
        supplierOrganisationId);
  }

  @TendersRetryable
  public AssessmentResult save(final AssessmentResult assessmentResult) {
    return assessmentResultRepo.save(assessmentResult);
  }

  @TendersRetryable
  public GCloudAssessmentResult save(final GCloudAssessmentResult assessmentResult) {
    return gCloudAssessmentResultRepo.save(assessmentResult);
  }

  @TendersRetryable
  public ProjectUserMapping save(final ProjectUserMapping projectUserMapping) {
    return projectUserMappingRepo.save(projectUserMapping);
  }

  @TendersRetryable
  public void delete(final ProjectUserMapping projectUserMapping) {
    projectUserMappingRepo.delete(projectUserMapping);
  }

  @TendersRetryable
  public void deleteAll(final List<ProjectUserMapping> projectUserMappings) {
    projectUserMappingRepo.deleteAll(projectUserMappings);
  }

  @TendersRetryable
  public void deleteGcloudAssessmentResultsById(final Integer assessmentId) {
    gCloudAssessmentResultRepo.deleteAllByAssessmentId(assessmentId);
  }

  @TendersRetryable
  public List<ProjectUserMapping> saveAll(final List<ProjectUserMapping> projectUserMappings) {
    return projectUserMappingRepo.saveAll(projectUserMappings);
  }

  @TendersRetryable
  public Set<ProjectUserMapping> findProjectUserMappingByProjectId(final Integer projectId) {
    return projectUserMappingRepo.findByProjectId(projectId);
  }

  @TendersRetryable
  public Set<ProjectUserMapping> findProjectUserMappingByUserId(final String userId) {
    return projectUserMappingRepo.findByUserId(userId);
  }

  @TendersRetryable
  public List<ProjectUserMapping> findProjectUserMappingByUserId(final String userId,
      final Pageable pageable) {
    return projectUserMappingRepo.findByUserId(userId, pageable);
  }

  @TendersRetryable
  public Optional<ProjectUserMapping> findProjectUserMappingByProjectIdAndUserId(
      final Integer projectId, final String userId) {
    return projectUserMappingRepo.findByProjectIdAndUserId(projectId, userId);
  }

  @TendersRetryable
  public void delete(final SupplierSelection supplierSelection) {
    supplierSelectionRepo.delete(supplierSelection);
  }

  @TendersRetryable
  public Set<BuyerUserDetails> findByExported(final boolean exported) {
    return buyerUserDetailsRepo.findByExported(exported);
  }

  @TendersRetryable
  public void saveAll(final Iterable<BuyerUserDetails> buyerUserDetails) {
    buyerUserDetailsRepo.saveAllAndFlush(buyerUserDetails);
  }

  @TendersRetryable
  public List<BuyerUserDetails> findAll() {
    return buyerUserDetailsRepo.findAll();
  }
  
  @TendersRetryable
  public ContractDetails save(final ContractDetails awardDetails) {
    return contractDetailsRepo.saveAndFlush(awardDetails);
  }
  
  @TendersRetryable
  public Optional<ContractDetails> findByEventId(final Integer eventId) {
    return contractDetailsRepo.findByEventId(eventId);
  }

  /**
   * Catch-all recovery method to wrap original exception in {@link ExhaustedRetryException} and
   * re-throw. Note - signature must match retried method.
   *
   * @param e the original exception
   * @param arg argument(s) matching the retried method
   * @return object same return type as retried method
   */
  @Recover
  public Object retriesExhausted(final Throwable e, final Object arg) {
    throw new ExhaustedRetryException("Retries exhausted", e);
  }

}
