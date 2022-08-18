package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Set;
import org.springframework.http.MediaType;
import lombok.experimental.UtilityClass;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;

/**
 * Global constant values
 */
@UtilityClass
public class Constants {

  // Remote service response related
  public static final String OK_MSG = "OK";

  // Security related
  public static final String JWT_CLAIM_SUBJECT = "sub";
  public static final String JWT_CLAIM_CII_ORG_ID = "ciiOrgId";
  public static final String ERR_MSG_UNAUTHORISED = "Missing, expired or invalid access token";
  public static final String ERR_MSG_FORBIDDEN = "Access to the requested resource is forbidden";
  public static final String ERR_MSG_DEFAULT = "An error occurred processing the request";
  public static final String ERR_MSG_UPSTREAM = "An error occurred invoking an upstream service";
  public static final String ERR_MSG_VALIDATION = "Validation error processing the request";
  public static final String ERR_MSG_RESOURCE_NOT_FOUND = "Resource not found";

  /**
   * {procurement-event-id}-{event-type}-{document-template-filename} (ODT)
   */
  public static final String GENERATED_DOCUMENT_FILENAME_FMT = "%s-%s-%s";

  public static final int WEBCLIENT_DEFAULT_RETRIES = 3;
  public static final int WEBCLIENT_DEFAULT_DELAY = 2;

  public static final MediaType MEDIA_TYPE_ODT =
      MediaType.parseMediaType("application/vnd.oasis.opendocument.text");

  public static final MediaType MEDIA_TYPE_DOCX = MediaType
      .parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

  public static final Set<DefineEventType> ASSESSMENT_EVENT_TYPES =
      Set.of(DefineEventType.FC, DefineEventType.FCA, DefineEventType.DA,DefineEventType.DAA,DefineEventType.PA);

  public static final Set<DefineEventType> DATA_TEMPLATE_EVENT_TYPES =
      Set.of(DefineEventType.RFI, DefineEventType.EOI, DefineEventType.FC);

  public static final Set<ViewEventType> TENDER_DB_ONLY_EVENT_TYPES =
      Set.of(ViewEventType.FCA, ViewEventType.DAA);

  public static final Set<ViewEventType> NOT_ALLOWED_EVENTS_AFTER_AWARD =
      Set.of(ViewEventType.FC, ViewEventType.DA, ViewEventType.EOI, ViewEventType.RFI,ViewEventType.PA);

  public static final Set<ViewEventType> TENDER_NON_DB_EVENT_TYPES =
      Set.of(ViewEventType.EOI, ViewEventType.RFI, ViewEventType.FC, ViewEventType.DA);


  public static final String UNLIMITED_VALUE = "1000000";

}
