package uk.gov.crowncommercial.dts.scale.cat.config;

import java.lang.annotation.*;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.TransactionException;
import uk.gov.crowncommercial.dts.scale.cat.exception.NotificationApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * Central retry configuration - see usage in {@link RetryableTendersDBDelegate}
 */
@Retryable(
    include = {TransientDataAccessException.class, TransactionException.class,
        NotificationApplicationException.class},
    maxAttemptsExpression = "${config.retry.maxAttempts}",
    backoff = @Backoff(delayExpression = "${config.retry.delay}",
        multiplierExpression = "${config.retry.multiplier}"))
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TendersRetryable {
}
