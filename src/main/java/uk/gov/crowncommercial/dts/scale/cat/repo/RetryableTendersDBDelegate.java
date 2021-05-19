package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
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

  static final int MAX_ATTEMPTS = 5;
  static final long DELAY = 1000;
  static final long MAX_DELAY = 20000;

  private final ProcurementProjectRepo procurementProjectRepo;
  private final ProcurementEventRepo procurementEventRepo;

  @Retryable(include = {TransientDataAccessException.class, TransactionException.class},
      maxAttempts = MAX_ATTEMPTS, backoff = @Backoff(delay = DELAY, maxDelay = MAX_DELAY))
  public ProcurementProject save(ProcurementProject procurementProject) {
    return procurementProjectRepo.save(procurementProject);
  }

  @Retryable(include = {TransientDataAccessException.class, TransactionException.class},
      maxAttempts = MAX_ATTEMPTS, backoff = @Backoff(delay = DELAY, maxDelay = MAX_DELAY))
  public ProcurementEvent save(ProcurementEvent procurementevent) {
    return procurementEventRepo.save(procurementevent);
  }

  @Retryable(include = {TransientDataAccessException.class, TransactionException.class},
      maxAttempts = MAX_ATTEMPTS, backoff = @Backoff(delay = DELAY, maxDelay = MAX_DELAY))
  public Optional<ProcurementProject> findProcurementProjectById(Integer id) {
    return procurementProjectRepo.findById(id);
  }

}
