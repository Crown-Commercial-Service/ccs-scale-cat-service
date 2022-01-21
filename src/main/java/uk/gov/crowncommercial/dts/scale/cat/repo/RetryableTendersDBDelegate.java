package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.TendersDBRetryable;<<<<<<<Upstream,based on origin/feature/capability_assessment
import uk.gov.crowncommercial.dts.scale.cat.model.entity.JourneyEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.*;=======
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;>>>>>>>0665 c15(RFI)Document generation and upload to Jaggaer rfx(#143)

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
  private final AssessmentRepo assessmentRepo;
  private final AssessmentToolRepo assessmentToolRepo;
  private final AssessmentDimensionWeightingRepo assessmentDimensionWeightingRepo;
  private final DimensionRepo dimensionRepo;
  private final AssessmentSelectionRepo assessmentSelectionRepo;
  private final RequirementTaxonRepo requirementTaxonRepo;
  private final SubmissionTypeRepo submissionTypeRepo;
  private final DocumentTemplateRepo documentTemplateRepo;

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
  public Optional<OrganisationMapping> findOrganisationMappingByOrgId(final String organisationId) {
    return organisationMappingRepo.findByOrganisationId(organisationId);
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
<<<<<<< Upstream, based on origin/feature/capability_assessment

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
  public Optional<AssessmentTool> findAssessmentToolByExternalToolId(final String externalToolId) {
    return assessmentToolRepo.findByExternalToolId(externalToolId);
  }

  @TendersDBRetryable
  public Set<DimensionEntity> findDimensionsByToolId(final Integer toolId) {
    return dimensionRepo.findByAssessmentTaxonsToolId(toolId);
  }

  @TendersDBRetryable
  public AssessmentSelection save(final AssessmentSelection journey) {
    return assessmentSelectionRepo.save(journey);
  }

  @TendersDBRetryable
  public Optional<RequirementTaxon> findRequirementTaxon(final Integer requirementId,
      final Integer toolId) {
    return requirementTaxonRepo.findByRequirementIdAndTaxonToolId(requirementId, toolId);
  }

  @TendersDBRetryable
  public Optional<DimensionEntity> findDimensionByName(final String name) {
    return dimensionRepo.findByName(name);
  }

  @TendersDBRetryable
  public List<SubmissionType> findAllSubmissionTypes() {
    return submissionTypeRepo.findAll();
  }

  @TendersDBRetryable
  public Optional<DocumentTemplate> findByEventType(final String eventType) {
    return documentTemplateRepo.findByEventType(eventType);

  }

  @TendersDBRetryable
  public Set<ProcurementEvent> findProcurementEventsByProjectId(final Integer projectId) {
    return procurementEventRepo.findByProjectId(projectId);
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
