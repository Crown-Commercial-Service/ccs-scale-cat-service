package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.CannotCreateTransactionException;

import uk.gov.crowncommercial.dts.scale.cat.config.RetryConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.repo.AssessmentDimensionWeightingRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.AssessmentRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.AssessmentResultRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.AssessmentSelectionRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.AssessmentTaxonRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.AssessmentToolRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ContractDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.DimensionRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.DocumentTemplateRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.GCloudAssessmentRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.GCloudAssessmentResultRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.JourneyRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.OrganisationMappingRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementStageEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProjectUserMappingRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.QuestionAndAnswerRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RequirementTaxonRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.repo.SupplierSelectionRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.SupplierSubmissionRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.readonly.CalculationBaseRepo;

/**
 *
 */
@ExtendWith(MockitoExtension.class)
class RetryableTendersDBDelegateTest {

  @Mock
  private ProcurementProjectRepo procurementProjectRepo;

  @Mock
  private ProcurementEventRepo procurementEventRepo;

  @Mock
  private ProcurementStageEventRepo procurementStageEventRepo;

  @Mock
  private GCloudAssessmentRepo gCloudAssessmentRepo;
  
  @Mock
  private GCloudAssessmentResultRepo gCloudAssessmentResultRepo;

  @Mock
  private OrganisationMappingRepo organisationMappingRepo;

  @Mock
  private JourneyRepo journeyRepo;

  @Mock
  private DocumentTemplateRepo documentTemplateRepo;

  @Mock
  private AssessmentRepo assessmentRepo;

  @Mock
  private AssessmentToolRepo assessmentToolRepo;

  @Mock
  private AssessmentDimensionWeightingRepo assessmentDimensionWeightingRepo;

  @Mock
  private DimensionRepo dimensionRepo;

  @Mock
  private AssessmentSelectionRepo assessmentSelectionRepo;

  @Mock
  private RequirementTaxonRepo requirementTaxonRepo;

  @Mock
  private AssessmentTaxonRepo assessmentTaxonRepo;

  @Mock
  private CalculationBaseRepo calculationBaseRepo;

  @Mock
  private ProjectUserMappingRepo projectUserMappingRepo;

  @Mock
  private SupplierSelectionRepo supplierSelectionRepo;

  @Mock
  private SupplierSubmissionRepo supplierSubmissionRepo;

  @InjectMocks
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @Mock
  private AssessmentResultRepo assessmentResultRepo;

  @Mock
  private BuyerUserDetailsRepo buyerUserDetailsRepo;
  
  @Mock
  private ContractDetailsRepo contractDetailsRepo;
  
  @Mock
  private QuestionAndAnswerRepo questionAndAnswerRepo;

  private RetryTemplate retryTemplate;

  @BeforeEach
  void setupRetryTemplate() {
    // Reuse your RetryConfig for logging listener
    RetryConfig retryConfig = new RetryConfig();

    retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(new SimpleRetryPolicy(5)); // same as prod retry count
    retryTemplate.registerListener(retryConfig.loggingRetryListener());
  }


  @Test
  void testRetrySuccess() {
    var transactionException = new CannotCreateTransactionException("DB problem");
    var procurementProject = new ProcurementProject();

    // Should retry 5 times, succeeding on final attempt
    when(procurementProjectRepo.saveAndFlush(procurementProject)).thenThrow(transactionException)
        .thenReturn(procurementProject);

    retryTemplate.execute(ctx -> retryableTendersDBDelegate.save(procurementProject));


    verify(procurementProjectRepo, times(2)).saveAndFlush(any(ProcurementProject.class));
  }

  @Test
  void testRetyExhaustion() {
    var transactionException = new CannotCreateTransactionException("DB problem");
    var procurementProject = new ProcurementProject();

    when(procurementProjectRepo.saveAndFlush(any()))
            .thenThrow(transactionException);

    var exhaustedRetryEx = assertThrows(ExhaustedRetryException.class,
            () -> retryTemplate.execute(
                    ctx -> retryableTendersDBDelegate.save(procurementProject),
                    ctx -> { throw new ExhaustedRetryException("Retries exhausted", ctx.getLastThrowable()); }
            ));

    assertTrue(exhaustedRetryEx.getMessage().startsWith("Retries exhausted"));
    assertSame(transactionException, exhaustedRetryEx.getCause());
    verify(procurementProjectRepo, times(5)).saveAndFlush(any(ProcurementProject.class));
  }

  @Test
  void shouldExtractIdFromEventId() {
      assertNull(retryableTendersDBDelegate.extractIdFromEventId(null));
      assertEquals(123, retryableTendersDBDelegate.extractIdFromEventId("abc-def-123"));
      assertEquals(25964, retryableTendersDBDelegate.extractIdFromEventId("ocds-pfhb7i-25964"));
  }
}
