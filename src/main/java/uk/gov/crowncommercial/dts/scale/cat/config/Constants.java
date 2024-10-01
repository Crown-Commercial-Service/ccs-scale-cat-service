package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import lombok.experimental.UtilityClass;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TerminationType;
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

  public static final String ERR_MSG_RFX_NOT_FOUND = "Rfx [%s] not found in Jaggaer";

  public static final String ERR_MSG_JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";

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
      Set.of(ViewEventType.FCA, ViewEventType.DAA,ViewEventType.PA);

  public static final Set<ViewEventType> NOT_ALLOWED_EVENTS_AFTER_AWARD =
      Set.of(ViewEventType.FC, ViewEventType.DA, ViewEventType.EOI, ViewEventType.RFI);

  public static final Set<ViewEventType> TENDER_NON_DB_EVENT_TYPES =
      Set.of(ViewEventType.EOI, ViewEventType.RFI, ViewEventType.FC, ViewEventType.DA);

  public static final Set<ViewEventType> ASSESMENT_COMPLETE_EVENT_TYPES =
          Set.of(ViewEventType.PA,ViewEventType.FCA, ViewEventType.DAA);

  public static final Set<ViewEventType> COMPLETE_EVENT_TYPES =
          Set.of(ViewEventType.RFI,ViewEventType.EOI, ViewEventType.DA, ViewEventType.DAA, ViewEventType.FC, ViewEventType.FCA); // We can create a new set specifically for the extra event type checking for new UI, if it effects the other usage.

  public static final Set<ViewEventType> FC_DA_NON_COMPLETE_EVENT_TYPES =
          Set.of(ViewEventType.FC,ViewEventType.DA);

  public static final String UNLIMITED_VALUE = "1000000";



  public static final String TO_BE_EVALUATED_STATUS = "To be Evaluated";
  public static final String EVALUATED_STATUS = "Final Evaluation";
  public static final String CLOSED_STATUS = "CLOSED";

  public static final String CANCELLED_STATUS = "cancelled";
  public static final String COMPLETE_STATUS = "COMPLETE";

  public static final List<String> CLOSED_STATUS_LIST = Arrays.asList(CLOSED_STATUS.toLowerCase(), TerminationType.CANCELLED.getValue(),TerminationType.WITHDRAWN.getValue(),
          TerminationType.UNSUCCESSFUL.getValue(), COMPLETE_STATUS.toLowerCase());

  public static final String LOT_PREFIX = "Lot ";
}
