package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.TendersRetryable;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BuyerUserDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ContractDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.JourneyEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProjectUserMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.SupplierSelection;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentDimensionWeighting;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentResult;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentSelection;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTaxon;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.DimensionEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudAssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudAssessmentResult;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.RequirementTaxon;
import uk.gov.crowncommercial.dts.scale.cat.repo.projection.AssessmentProjection;
import uk.gov.crowncommercial.dts.scale.cat.repo.readonly.CalculationBaseRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.specification.ProjectSearchCriteria;
import uk.gov.crowncommercial.dts.scale.cat.repo.specification.ProjectSearchSpecification;

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
  private final QuestionAndAnswerRepo questionAndAnswerRepo;


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
  @Transactional
  public void deleteProcurementProjectById(final Integer id) {
      procurementProjectRepo.deleteById(id);
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
  @Transactional
  public void deleteProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(final Integer eventIdKey, final String ocdsAuthorityName, final String ocidPrefix) {
    procurementEventRepo.deleteByIdAndOcdsAuthorityNameAndOcidPrefix(eventIdKey, ocdsAuthorityName, ocidPrefix);
  }

  @TendersRetryable
  @Cacheable(value = "tendersCache", key = "#root.methodName + '-' + #organisationIds")
  public Set<OrganisationMapping> findOrganisationMappingByOrganisationIdIn(
      final Set<String> organisationIds) {
    return organisationMappingRepo.findByOrganisationIdIn(organisationIds);
  }

  @TendersRetryable
  public Set<OrganisationMapping> findOrganisationMappingByExternalOrganisationIdIn(
          final Set<Integer> bravoIds) {
    return organisationMappingRepo.findByExternalOrganisationIdIn(bravoIds);
  }



  @TendersRetryable
  @Cacheable(value = "tendersCache", key = "#root.methodName + '-' + #externalOrganisationId")
  public Optional<OrganisationMapping> findOrganisationMappingByExternalOrganisationId(
      final Integer externalOrganisationId) {
    return organisationMappingRepo.findByExternalOrganisationId(externalOrganisationId);
  }

  @TendersRetryable
  public OrganisationMapping save(final OrganisationMapping organisationMapping) {
    return organisationMappingRepo.saveAndFlush(organisationMapping);
  }

  @TendersRetryable
  @Cacheable(value = "tendersCache", key = "#root.methodName + '-' + #organisationId")
  public Optional<OrganisationMapping> findOrganisationMappingByOrganisationId(
      final String organisationId) {
    return organisationMappingRepo.findByOrganisationId(organisationId);
  }

  @TendersRetryable
  public Set<OrganisationMapping> findOrganisationMappingByCasOrganisationIdIn(final Set<String> organisationIds) {
    return organisationMappingRepo.findByCasOrganisationIdIn(organisationIds);
  }

  @TendersRetryable
  @Cacheable(value = "tendersCache", key = "#root.methodName + '-' + #organisationId")
  public Optional<OrganisationMapping> findOrganisationMappingByCasOrganisationId(
          final String organisationId) {
    return organisationMappingRepo.findByCasOrganisationId(organisationId);
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
  public Set<DocumentTemplate> findByEventTypeAndCommercialAgreementNumberAndLotNumber(final String eventType, final String commercialAgreementNumber, final String lotNumber) {
    return documentTemplateRepo.findByEventTypeAndCommercialAgreementNumberAndLotNumber(eventType, commercialAgreementNumber, lotNumber);
  }
  
  @TendersRetryable
  public Set<DocumentTemplate> findByEventTypeAndCommercialAgreementNumberAndLotNumberAndTemplateGroup(final String eventType, final String commercialAgreementNumber, final String lotNumber, final Integer templateGroup) {
    // We need to potentially correct our Lot ID - we need to be sure it contains the legacy prefix for doc gen
    String lotId = lotNumber;

    if (!lotId.contains(Constants.LOT_PREFIX)) {
      lotId = Constants.LOT_PREFIX + lotId;
    }

    if (templateGroup == null) {
      return this.findByEventTypeAndCommercialAgreementNumberAndLotNumber(eventType, commercialAgreementNumber, lotId);
    }

    return documentTemplateRepo
        .findByEventTypeAndCommercialAgreementNumberAndLotNumberAndTemplateGroup(eventType, commercialAgreementNumber, lotId, templateGroup);
  }
  
  @TendersRetryable
  public Set<DocumentTemplate> findByEventStage(final String eventStage) {
    return documentTemplateRepo.findByEventStage(eventStage);
  }
  
  @TendersRetryable
  public Set<DocumentTemplate> findByEventStageAndAgreementNumber(final String eventStage, final String agreementNumber) {
    return documentTemplateRepo.findByEventStageAndCommercialAgreementNumber(eventStage, agreementNumber);
  }

  @TendersRetryable
  public Set<ProcurementEvent> findProcurementEventsByProjectId(final Integer projectId) {
    return procurementEventRepo.findByProjectId(projectId);
  }

  @TendersRetryable
  public Set<AssessmentProjection> findAssessmentsProjectionForUserWithExternalId(final String userId, Integer externalToolId) {
    return assessmentRepo.findAssessmentsByCreatedByAndExternalToolId(userId,externalToolId);
  }

  @TendersRetryable
  public Set<AssessmentProjection> findAssessmentsProjectionForUser(final String userId) {
    return assessmentRepo.findAssessmentsByCreatedBy(userId);
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
  public List<GCloudAssessmentResult> saveAll(final Set<GCloudAssessmentResult> assessmentResults) {
    return gCloudAssessmentResultRepo.saveAll(assessmentResults);
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
  public void deleteGcloudAssessmentById(final Integer assessmentId) {
    gcloudAssessmentRepo.deleteAllById(assessmentId);
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
  @Transactional
  public void deleteProjectUserMappingByProjectId(final Integer projectId) {
    projectUserMappingRepo.deleteByProjectId(projectId);
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
  public List<ProjectUserMapping> findProjectUserMappingByUserId(final String userId,final String searchType,final String searchTerm,
                                                                 final Pageable pageable) {

    ProjectSearchSpecification specification= new ProjectSearchSpecification(new ProjectSearchCriteria(searchType,searchTerm,userId));

    Page<ProjectUserMapping> page=projectUserMappingRepo.findAll(specification, pageable);
    return page.getContent();
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
  
  @TendersRetryable
  @Transactional(readOnly = true)
  public Set<ProcurementProject> findPublishedEventsByAgreementId(final String agreementId) {
    return procurementProjectRepo.findPublishedEventsByAgreementId(agreementId);
  }
  
  @TendersRetryable
  @Transactional(readOnly = true)
  public long findQuestionsCountByEventId(final Integer eventId) {
    return questionAndAnswerRepo.countByEventId(eventId);
  }
  
  public void updateEventDate(ProcurementEvent procurementEvent, String profile) {
    procurementEvent.setUpdatedBy(profile);
    procurementEvent.setUpdatedAt(Instant.now());
    this.save(procurementEvent);
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

    /**
     * Removes a given OrganisationMapping looked up via OrganisationId from the cache.  Called when we're changing state in a way that would affect the cache
     */
    @CacheEvict(value = "tendersCache", key = "'findOrganisationMappingByOrganisationId-' + #orgId")
    public void removeMappingByOrgIdFromCache(String orgId) {
        // Take no action here - the method annotation deals with the action
    }

    /**
     * Removes a given OrganisationMapping looked up via CAS OrganisationId from the cache.  Called when we're changing state in a way that would affect the cache
     */
    @CacheEvict(value = "tendersCache", key = "'findOrganisationMappingByCasOrganisationId-' + #orgId")
    public void removeMappingByCasOrgIdFromCache(String orgId) {
        // Take no action here - the method annotation deals with the action
    }
}
