package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple retry listener that logs details of each attempt
 */
@Component
@Slf4j
public class LoggingRetryListener extends RetryListenerSupport {

  @Override
  public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
      Throwable throwable) {
    log.warn("Retryable method {} threw {}th exception {}", context.getAttribute("context.name"),
        context.getRetryCount(), throwable.toString());
  }

}
