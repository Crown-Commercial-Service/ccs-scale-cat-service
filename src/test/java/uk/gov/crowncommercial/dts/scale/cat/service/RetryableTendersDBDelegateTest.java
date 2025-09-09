package uk.gov.crowncommercial.dts.scale.cat.service;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.CannotCreateTransactionException;
import uk.gov.crowncommercial.dts.scale.cat.config.RetryConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.repo.*;
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


}
