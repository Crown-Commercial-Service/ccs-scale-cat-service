package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.CannotCreateTransactionException;
import uk.gov.crowncommercial.dts.scale.cat.config.RetryConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.TendersRetryable;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxTemplateMapping;
import uk.gov.crowncommercial.dts.scale.cat.repo.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.readonly.CalculationBaseRepo;

/**
 *
 */
@SpringBootTest(classes = {RetryableTendersDBDelegate.class, RetryConfig.class},
    webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class RetryableTendersDBDelegateTest {

  @MockBean
  private ProcurementProjectRepo procurementProjectRepo;

  @MockBean
  private ProcurementEventRepo procurementEventRepo;

  @MockBean
  private GCloudAssessmentRepo gCloudAssessmentRepo;
  
  @MockBean
  private GCloudAssessmentResultRepo gCloudAssessmentResultRepo;

  @MockBean
  private OrganisationMappingRepo organisationMappingRepo;

  @MockBean
  private JourneyRepo journeyRepo;

  @MockBean
  private DocumentTemplateRepo documentTemplateRepo;

  @MockBean
  private AssessmentRepo assessmentRepo;

  @MockBean
  private AssessmentToolRepo assessmentToolRepo;

  @MockBean
  private AssessmentDimensionWeightingRepo assessmentDimensionWeightingRepo;

  @MockBean
  private DimensionRepo dimensionRepo;

  @MockBean
  private AssessmentSelectionRepo assessmentSelectionRepo;

  @MockBean
  private RequirementTaxonRepo requirementTaxonRepo;

  @MockBean
  private AssessmentTaxonRepo assessmentTaxonRepo;

  @MockBean
  private CalculationBaseRepo calculationBaseRepo;

  @MockBean
  private ProjectUserMappingRepo projectUserMappingRepo;

  @MockBean
  private SupplierSelectionRepo supplierSelectionRepo;

  @MockBean
  private SupplierSubmissionRepo supplierSubmissionRepo;

  @Autowired
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @MockBean
  private AssessmentResultRepo assessmentResultRepo;

  @MockBean
  private BuyerUserDetailsRepo buyerUserDetailsRepo;
  
  @MockBean
  private ContractDetailsRepo contractDetailsRepo;
  
  @MockBean
  private RfxTemplateMappingRepo rfxTemplateMappingRepo;
  

  @Test
  void testRetrySuccess() {
    var transactionException = new CannotCreateTransactionException("DB problem");
    var procurementProject = new ProcurementProject();

    // Should retry 5 times, succeeding on final attempt
    when(procurementProjectRepo.saveAndFlush(procurementProject)).thenThrow(transactionException)
        .thenReturn(procurementProject);

    retryableTendersDBDelegate.save(procurementProject);

    verify(procurementProjectRepo, times(2)).saveAndFlush(any(ProcurementProject.class));
  }

  @Test
  void testRetyExhaustion() {
    var transactionException = new CannotCreateTransactionException("DB problem");
    var queryTimeoutException = new QueryTimeoutException("Another DB problem");
    var procurementProject = new ProcurementProject();

    // Should fail 5 times
    when(procurementProjectRepo.saveAndFlush(procurementProject)).thenThrow(transactionException,
        queryTimeoutException, transactionException);

    var exhaustedRetryEx = assertThrows(ExhaustedRetryException.class,
        () -> retryableTendersDBDelegate.save(procurementProject));

    assertTrue(exhaustedRetryEx.getMessage().startsWith("Retries exhausted"));
    assertSame(transactionException, exhaustedRetryEx.getCause());
    verify(procurementProjectRepo, times(3)).saveAndFlush(any(ProcurementProject.class));
  }

}
