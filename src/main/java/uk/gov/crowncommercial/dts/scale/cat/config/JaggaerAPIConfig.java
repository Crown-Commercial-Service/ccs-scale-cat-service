package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.jaggaer", ignoreUnknownFields = true)
@Data
public class JaggaerAPIConfig {

  public static final String ENDPOINT = "endpoint";

  private String baseUrl;
  private String headerValueWWWAuthenticate;
  private String headerValueInvalidContentType;
  private Integer timeoutDuration;
  private Map<Integer, TenderStatus> rfxStatusToTenderStatus;
  private Boolean addDivisionToProjectTeam;
  private Long tokenExpirySeconds;
  private Map<String, String> createProject;
  private Map<String, String> createRfx;
  private Map<String, String> getBuyerCompanyProfile;
  private Map<String, String> exportRfx;

}
