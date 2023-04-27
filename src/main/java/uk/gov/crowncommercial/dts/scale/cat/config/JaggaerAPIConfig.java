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
  public static final String PRINCIPAL_PLACEHOLDER = "{{PRINCIPAL}}";
  public static final String DUNS_PLACEHOLDER = "{{DUNSNumber}}";
  public static final String SSO_CODE_VALUE = "OPEN_ID";

  private String baseUrl;
  private String headerValueWWWAuthenticate;
  private String headerValueInvalidContentType;
  private String selfServiceId;
  private String assistedProcurementUserId;
  private String assistedProcurementId;
  private String assistedProcurementOrgId;
  private String defaultBuyerRightsProfile;
  private String defaultSupplierRightsProfile;
  private Integer timeoutDuration;
  private Map<Integer, TenderStatus> rfxStatusToTenderStatus;
  private Map<Integer, Map<String, TenderStatus>> rfxStatusAndEventTypeToTenderStatus;
  private Boolean addDivisionToProjectTeam;
  private Long tokenExpirySeconds;
  private Map<String, String> createProject;
  private Map<String, String> getProject;
  private Map<String, String> getProjectList;
  private Map<String, String> createRfx;
  private Map<String, String> getBuyerCompanyProfile;
  private Map<String, String> exportRfx;
  private Map<String, String> exportRfxWithEmailRecipients;
  private Map<String, String> exportRfxWithSuppliers;
  private Map<String, String> exportRfxWithSuppliersOffersAndResponseCounters;
  private Map<String, String> exportRfxWithBuyerAndSellerAttachments;
  private Map<String, String> getSupplierCompanyProfileByBravoID;
  private Map<String, String> getSupplierCompanyProfileBySSOUserLogin;
  private Map<String, String> getSupplierSubUserProfile;
  private Map<String, String> getCompanyProfileByDUNSNumber;
  private Map<String, String> getCompanyProfileByBravoID;
  private Map<String, String> createUpdateCompany;
  private Map<String, String> getAttachment;
  private Map<String, String> publishRfx;
  private Map<String, String> extendRfx;
  private Map<String, String> createReplyMessage;
  private Map<String, String> creatUpdateScores;
  private Map<String, String> getMessages;
  private Map<String, String> getMessage;
  private Map<String, String> updateMessage;
  private Map<String, String> searchRfxSummary;
  private Map<String, String> getRfxByComponent;
  private Map<String, String> award;
  private Map<String, String> preAward;
  private Map<String, String> completeTechnical;
  private Map<String, String> apiDefaults;
  private Map<String, String> getRfxByLastUpdateDateList;
  private Map<String, String> esourcing;

  // Temporary - SOAP config
  private JaggaerSOAPAPIConfig soap;
  private Map<String, String> startEvaluation;
  private Map<String, String> openEnvelope;
  private Map<String, String> invalidateEvent;
}
