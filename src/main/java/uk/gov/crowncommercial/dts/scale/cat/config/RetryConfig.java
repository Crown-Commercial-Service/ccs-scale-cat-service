package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.listener.RetryListenerSupport;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Retry config, including a listener that logs details of each attempt
 */
@Configuration
@EnableRetry
@Slf4j
public class RetryConfig {

  @Bean
  public RetryListenerSupport loggingRetryListener() {
    return new RetryListenerSupport() {

      @Override
      public <T, E extends Throwable> void onError(RetryContext context,
          RetryCallback<T, E> callback, Throwable throwable) {
        log.warn("Retryable method {} threw {}th exception {}",
            context.getAttribute("context.name"), context.getRetryCount(), throwable.toString());
      }
    };
  }

}
