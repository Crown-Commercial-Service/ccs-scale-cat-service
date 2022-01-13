package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.TendersDBRetryable;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;

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
  public Optional<ProcurementProject> findProcurementExternalProjectById(final String externalProjectId) {
    return procurementProjectRepo.findByExternalProjectId(externalProjectId);
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
  public Optional<DocumentTemplate> findByEventType(final String eventType) {
    return documentTemplateRepo.findByEventType(eventType);
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
