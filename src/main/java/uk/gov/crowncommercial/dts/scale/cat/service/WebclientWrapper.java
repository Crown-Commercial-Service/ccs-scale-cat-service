package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;

/**
 * Experimental - {@link WebClient} generic helper functions. Primarily to avoid having to mock the
 * webclient invocation chain in service class tests!
 */
@Service
@Slf4j
public class WebclientWrapper {

  static final boolean is5xxServerError(final Throwable throwable) {
    return throwable instanceof WebClientResponseException
        && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError();
  }

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
        .onErrorMap(IOException.class, UncheckedIOException::new)
        .retryWhen(Retry
            .fixedDelay(Constants.WEBCLIENT_DEFAULT_RETRIES,
                Duration.ofSeconds(Constants.WEBCLIENT_DEFAULT_DELAY))
            .filter(WebclientWrapper::is5xxServerError))
        .onErrorResume(WebClientResponseException.class, funcFallback404)
        .block(Duration.ofSeconds(timeoutDuration)));
  }

  /**
   * Uses the passed webclient, uri template and optional params to post the given data to the
   * remote service
   *
   * @param <T>
   * @param postData
   * @param responseType the expected response type
   * @param webclient
   * @param timeoutDuration
   * @param uriTemplate
   * @param params
   * @return responseType object
   * @throws JaggaerApplicationException if the response is null
   * @throws WebClientResponseException for all other errors
   */
  public <T> T postData(final Object postData, final Class<T> responseType,
      final WebClient webclient, final int timeoutDuration, final String uriTemplate,
      final Object... params) {

    // TODO: Make upstream service exeption generic
    return ofNullable(webclient.post().uri(uriTemplate, params).bodyValue(postData).retrieve()
        .bodyToMono(responseType).block(Duration.ofSeconds(timeoutDuration)))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error posting data to " + uriTemplate));
  }

  /**
   * Uses the passed webclient, uri template ,token and optional params to post the given data to
   * the remote service
   *
   * @param <T>
   * @param postData
   * @param responseType
   * @param webclient
   * @param timeoutDuration
   * @param uriTemplate
   * @param authToken
   * @param params
   * @return responseType object
   */
  public <T> T postDataWithToken(final Object postData, final Class<T> responseType,
      final WebClient webclient, final int timeoutDuration, final String uriTemplate,
      final String authToken, final Object... params) {

    // TODO: Make upstream service exeption generic
    return ofNullable(webclient.post().uri(uriTemplate, params)
        .headers(e -> e.setBearerAuth(authToken)).bodyValue(postData).retrieve()
        .bodyToMono(responseType).block(Duration.ofSeconds(timeoutDuration)))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error posting data to " + uriTemplate));
  }

}
