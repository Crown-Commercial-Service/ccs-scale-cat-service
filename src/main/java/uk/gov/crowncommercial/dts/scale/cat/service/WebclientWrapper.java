package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;

/**
 * Experimental - {@link WebClient} generic helper functions. Primarily to avoid having to mock the
 * webclient invocation chain in service class tests!
 */
@Service
public class WebclientWrapper {

  /**
   * Uses the passed webclient, uri template and optional args to attempt retrieval of a remote
   * resource, returning an {@link Optional} of the given resource or empty if a 404 not found is
   * received.
   *
   * @param <T>
   * @param resourceType the expected resource type
   * @param webclient
   * @param timeoutDuration
   * @param uriTemplate
   * @param params
   * @return optional of resourceType or empty if not found
   * @throws WebClientResponseException for all other errors
   */
  public <T> Optional<T> getOptionalResource(final Class<T> resourceType, final WebClient webclient,
      final int timeoutDuration, final String uriTemplate, final Object... params) {

    Function<WebClientResponseException, Mono<T>> funcFallback404 =
        ex -> ex.getRawStatusCode() == 404 ? Mono.empty() : Mono.error(ex);

    return ofNullable(webclient.get().uri(uriTemplate, params).retrieve().bodyToMono(resourceType)
        .retryWhen(Retry.fixedDelay(Constants.WEBCLIENT_DEFAULT_RETRIES,
            Duration.ofSeconds(Constants.WEBCLIENT_DEFAULT_DELAY)))
        .onErrorResume(WebClientResponseException.class, funcFallback404)
        .block(Duration.ofSeconds(timeoutDuration)));
  }

}
