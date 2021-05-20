package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

/**
 * Simple retrying delegate to JPA repos {@link ProcurementProjectRepo}
 */
@Service
@RequiredArgsConstructor
public class RetryableTendersDBDelegate {

  private final ProcurementProjectRepo procurementProjectRepo;
  private final ProcurementEventRepo procurementEventRepo;

  @Retryable(include = {TransientDataAccessException.class, TransactionException.class},
      maxAttemptsExpression = "${config.retry.maxAttempts}",
      backoff = @Backoff(delayExpression = "${config.retry.delay}",
          multiplierExpression = "${config.retry.multiplier}"))
  public ProcurementProject save(ProcurementProject procurementProject) {
    return procurementProjectRepo.save(procurementProject);
  }

  @Retryable(include = {TransientDataAccessException.class, TransactionException.class},
      maxAttemptsExpression = "${config.retry.maxAttempts}",
      backoff = @Backoff(delayExpression = "${config.retry.delay}",
          multiplierExpression = "${config.retry.multiplier}"))
  public ProcurementEvent save(ProcurementEvent procurementevent) {
    return procurementEventRepo.save(procurementevent);
  }

  @Retryable(include = {TransientDataAccessException.class, TransactionException.class},
      maxAttemptsExpression = "${config.retry.maxAttempts}",
      backoff = @Backoff(delayExpression = "${config.retry.delay}",
          multiplierExpression = "${config.retry.multiplier}"))
  public Optional<ProcurementProject> findProcurementProjectById(Integer id) {
    return procurementProjectRepo.findById(id);
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
  public Object retriesExhausted(Throwable e, Object arg) {
    throw new ExhaustedRetryException("Retries exhausted", e);
  }

}
