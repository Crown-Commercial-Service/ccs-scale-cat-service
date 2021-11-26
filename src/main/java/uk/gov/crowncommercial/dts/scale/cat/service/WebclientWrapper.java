package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

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
   * @param type the class type
   * @param webclient
   * @param uriTemplate
   * @param args
   * @return optional of type or empty if not found
   * @throws WebClientResponseException for all other errors
   */
  public <T> Optional<T> getOptionalResource(final Class<T> type, final WebClient webclient,
      final String uriTemplate, final Object... args) {

    Function<WebClientResponseException, Mono<T>> funcFallback404 =
        ex -> ex.getRawStatusCode() == 404 ? Mono.empty() : Mono.error(ex);

    return ofNullable(webclient.get().uri(uriTemplate, args).retrieve().bodyToMono(type)
        .onErrorResume(WebClientResponseException.class, funcFallback404)
        .block(Duration.ofSeconds(5)));
  }

}
