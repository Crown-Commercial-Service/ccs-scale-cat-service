package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class ConclaveService {

  private final WebClient conclaveWebClient;

}
