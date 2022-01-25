package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.TendersDBRetryable;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.JourneyEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentDimensionWeighting;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.Dimension;

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
  public Optional<AssessmentTool> findAssessmentToolByInternalName(final String internalName) {
    return assessmentToolRepo.findByInternalName(internalName);
  }

  @TendersDBRetryable
  public Set<Dimension> findDimensionsByToolId(final Integer toolId) {
    return dimensionRepo.findByAssessmentTaxonsToolId(toolId);
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
