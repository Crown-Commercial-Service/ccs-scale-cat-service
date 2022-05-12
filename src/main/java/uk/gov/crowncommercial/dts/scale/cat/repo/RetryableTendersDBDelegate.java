package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.TendersDBRetryable;
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

  @TendersDBRetryable
  public ProcurementProject save(final ProcurementProject procurementProject) {
    return procurementProjectRepo.saveAndFlush(procurementProject);
  }

  @TendersDBRetryable
  public ProcurementEvent save(final ProcurementEvent procurementevent) {
    return procurementEventRepo.save(procurementevent);
  }

  @TendersDBRetryable
  public Optional<ProcurementProject> findProcurementProjectById(final Integer id) {
    return procurementProjectRepo.findById(id);
  }

  @TendersDBRetryable
  public List<ProcurementProject> findByExternalProjectIdIn(final Set<String> externalProjectIds) {
    return procurementProjectRepo.findByExternalProjectIdIn(externalProjectIds);
  }

  @TendersDBRetryable
  public Optional<ProcurementEvent> findProcurementEventById(final Integer id) {
    return procurementEventRepo.findById(id);
  }

  @TendersDBRetryable
  public Optional<ProcurementEvent> findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
      final Integer eventIdKey, final String ocdsAuthorityName, final String ocidPrefix) {
    return procurementEventRepo.findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
        eventIdKey, ocdsAuthorityName, ocidPrefix);
  }

  @TendersDBRetryable
  public Set<OrganisationMapping> findOrganisationMappingByOrganisationIdIn(
      final Set<String> organisationIds) {
    return organisationMappingRepo.findByOrganisationIdIn(organisationIds);
  }

  @TendersDBRetryable
  public Optional<OrganisationMapping> findOrganisationMappingByExternalOrganisationId(
      final Integer externalOrganisationId) {
    return organisationMappingRepo.findByExternalOrganisationId(externalOrganisationId);
  }

  @TendersDBRetryable
  public OrganisationMapping save(final OrganisationMapping organisationMapping) {
    return organisationMappingRepo.saveAndFlush(organisationMapping);
  }

  @TendersDBRetryable
  public Optional<OrganisationMapping> findOrganisationMappingByOrganisationId(
      final String organisationId) {
    return organisationMappingRepo.findByOrganisationId(organisationId);
  }

  @TendersDBRetryable
  public JourneyEntity save(final JourneyEntity journey) {
    return journeyRepo.save(journey);
  }

  @TendersDBRetryable
  public Optional<JourneyEntity> findJourneyByExternalId(final String externalId) {
    return journeyRepo.findByExternalId(externalId);
  }

  @TendersDBRetryable
  public Optional<DocumentTemplate> findById(final Integer fileId) {
    return documentTemplateRepo.findById(fileId);
  }

  @TendersDBRetryable
  public Set<DocumentTemplate> findByEventType(final String eventType) {
    return documentTemplateRepo.findByEventType(eventType);
  }

  @TendersDBRetryable
  public Set<ProcurementEvent> findProcurementEventsByProjectId(final Integer projectId) {
    return procurementEventRepo.findByProjectId(projectId);
  }

  @TendersDBRetryable
  public Set<AssessmentEntity> findAssessmentsForUser(final String userId) {
    return assessmentRepo.findByTimestampsCreatedBy(userId);
  }

  @TendersDBRetryable
  public AssessmentEntity save(final AssessmentEntity assessment) {
    return assessmentRepo.saveAndFlush(assessment);
  }

  @TendersDBRetryable
  public Optional<AssessmentEntity> findAssessmentById(final Integer id) {
    return assessmentRepo.findById(id);
  }

  @TendersDBRetryable
  public AssessmentDimensionWeighting save(
      final AssessmentDimensionWeighting assessmentDimensionWeighting) {
    return assessmentDimensionWeightingRepo.saveAndFlush(assessmentDimensionWeighting);
  }

  @TendersDBRetryable
  public Optional<AssessmentTool> findAssessmentToolById(final Integer toolId) {
    return assessmentToolRepo.findById(toolId);
  }

  @TendersDBRetryable
  public Optional<AssessmentTool> findAssessmentToolByExternalToolId(final String externalToolId) {
    return assessmentToolRepo.findByExternalToolId(externalToolId);
  }

  @TendersDBRetryable
  public Set<DimensionEntity> findDimensionsByToolId(final Integer toolId) {
    return dimensionRepo.findByAssessmentTaxonsToolId(toolId);
  }

  @TendersDBRetryable
  public Set<AssessmentSelection> findByDimensionIdAndRequirementId(final Integer assessmentId,
      final Integer dimensionId, final Integer requirementId) {
    return assessmentSelectionRepo.findByDimensionIdAndRequirementTaxonRequirementId(dimensionId,
        requirementId);
  }

  @TendersDBRetryable
  public AssessmentSelection save(final AssessmentSelection assessmentSelection) {
    return assessmentSelectionRepo.save(assessmentSelection);
  }

  @TendersDBRetryable
  public Optional<RequirementTaxon> findRequirementTaxon(final Integer requirementId,
      final Integer toolId) {
    return requirementTaxonRepo.findByRequirementIdAndTaxonToolId(requirementId, toolId);
  }

  @TendersDBRetryable
  public Optional<DimensionEntity> findDimensionById(final Integer dimensionId) {
    return dimensionRepo.findById(dimensionId);
  }

  @TendersDBRetryable
  public Optional<DimensionEntity> findDimensionByName(final String name) {
    return dimensionRepo.findByName(name);
  }

  @TendersDBRetryable
  public Optional<AssessmentTaxon> findAssessmentTaxonById(final Integer assessmentTaxonId) {
    return assessmentTaxonRepo.findById(assessmentTaxonId);
  }

  @TendersDBRetryable
  public Set<CalculationBase> findCalculationBaseByAssessmentId(final Integer assessmentId) {
    return calculationBaseRepo.findByAssessmentId(assessmentId);
  }

  @TendersDBRetryable
  public Optional<AssessmentResult> findByAssessmentIdAndSupplierOrganisationId(
      final Integer assessmentId, final String supplierOrganisationId) {
    return assessmentResultRepo.findByAssessmentIdAndSupplierOrganisationId(assessmentId,
        supplierOrganisationId);
  }

  @TendersDBRetryable
  public AssessmentResult save(final AssessmentResult assessmentResult) {
    return assessmentResultRepo.save(assessmentResult);
  }

  @TendersDBRetryable
  public ProjectUserMapping save(final ProjectUserMapping projectUserMapping) {
    return projectUserMappingRepo.save(projectUserMapping);
  }

  @TendersDBRetryable
  public void delete(final ProjectUserMapping projectUserMapping) {
    projectUserMappingRepo.delete(projectUserMapping);
  }

  @TendersDBRetryable
  public void deleteAll(final List<ProjectUserMapping> projectUserMappings) {
    projectUserMappingRepo.deleteAll(projectUserMappings);
  }

  @TendersDBRetryable
  public List<ProjectUserMapping> saveAll(final List<ProjectUserMapping> projectUserMappings) {
    return projectUserMappingRepo.saveAll(projectUserMappings);
  }

  @TendersDBRetryable
  public Set<ProjectUserMapping> findProjectUserMappingByProjectId(final Integer projectId) {
    return projectUserMappingRepo.findByProjectId(projectId);
  }

  @TendersDBRetryable
  public Set<ProjectUserMapping> findProjectUserMappingByUserId(final String userId) {
    return projectUserMappingRepo.findByUserId(userId);
  }

  @TendersDBRetryable
  public List<ProjectUserMapping> findProjectUserMappingByUserId(final String userId,
      final Pageable pageable) {
    return projectUserMappingRepo.findByUserId(userId, pageable);
  }

  @TendersDBRetryable
  public Optional<ProjectUserMapping> findProjectUserMappingByProjectIdAndUserId(
      final Integer projectId, final String userId) {
    return projectUserMappingRepo.findByProjectIdAndUserId(projectId, userId);
  }

  @TendersDBRetryable
  public void delete(final SupplierSelection supplierSelection) {
    supplierSelectionRepo.delete(supplierSelection);
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
