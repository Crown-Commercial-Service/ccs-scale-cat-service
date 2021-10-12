package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.agreements-service", ignoreUnknownFields = true)
@Data
public class AgreementsServiceAPIConfig {

  public static final String ENDPOINT = "endpoint";

  private String baseUrl;
  private String apiKey;
  private Integer timeoutDuration;
  private Map<String, String> getAgreementDetail;
  private Map<String, String> getLotDetailsForAgreement;
  private Map<String, String> getLotEventRFITemplate;

}
