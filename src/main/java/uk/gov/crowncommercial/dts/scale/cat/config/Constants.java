package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Set;
import org.springframework.http.MediaType;
import lombok.experimental.UtilityClass;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;

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

  public static final int WEBCLIENT_DEFAULT_RETRIES = 3;
  public static final int WEBCLIENT_DEFAULT_DELAY = 2;

  public static final MediaType MEDIA_TYPE_ODT =
      MediaType.parseMediaType("application/vnd.oasis.opendocument.text");

  public static final Set<DefineEventType> ASSESSMENT_EVENT_TYPES =
      Set.of(DefineEventType.FCA, DefineEventType.DAA);
}
