package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.transaction.CannotCreateTransactionException;
import uk.gov.crowncommercial.dts.scale.cat.config.RetryConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 *
 */
@SpringBootTest(classes = {RetryableTendersDBDelegate.class, RetryConfig.class},
    webEnvironment = WebEnvironment.NONE)
class RetryableTendersDBDelegateTest {

  @MockBean
  private ProcurementProjectRepo procurementProjectRepo;

  @MockBean
  private ProcurementEventRepo procurementEventRepo;

  @Autowired
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @Test
  void testRetrySuccess() {
    var transactionException = new CannotCreateTransactionException("DB problem");
    var procurementProject = new ProcurementProject();

    // Should retry 5 times, succeeding on final attempt
    when(procurementProjectRepo.save(procurementProject)).thenThrow(transactionException,
        transactionException, transactionException, transactionException)
        .thenReturn(procurementProject);

    retryableTendersDBDelegate.save(procurementProject);

    verify(procurementProjectRepo, times(5)).save(any(ProcurementProject.class));
  }

  @Test
  void testRetyExhaustion() {
    var transactionException = new CannotCreateTransactionException("DB problem");
    var queryTimeoutException = new QueryTimeoutException("Another DB problem");
    var procurementProject = new ProcurementProject();

    // Should fail 5 times
    when(procurementProjectRepo.save(procurementProject)).thenThrow(transactionException,
        queryTimeoutException, transactionException, queryTimeoutException, transactionException);

    ExhaustedRetryException exhaustedRetryEx = assertThrows(ExhaustedRetryException.class,
        () -> retryableTendersDBDelegate.save(procurementProject));

    assertTrue(exhaustedRetryEx.getMessage().startsWith("Retries exhausted"));
    assertSame(transactionException, exhaustedRetryEx.getCause());
    verify(procurementProjectRepo, times(5)).save(any(ProcurementProject.class));
  }

}
