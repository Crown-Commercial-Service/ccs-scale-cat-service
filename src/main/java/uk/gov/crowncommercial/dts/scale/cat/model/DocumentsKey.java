package uk.gov.crowncommercial.dts.scale.cat.model;

import java.util.Base64;
import java.util.regex.Pattern;
import lombok.Value;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;

/**
 * Helper class to manage multiple Documents by type.
 *
 * A Document Key is an object that wraps the mutliple Documents and allows each value to be easily
 * accessed internally in the Tenders API.
 *
 */
@Value
public class DocumentsKey {

  public static final Pattern pattern =
      Pattern.compile("(?<audience>[A-Za-z]+)-(?<fileType>[\\s\\S]+)-(?<fileName>[\\s\\S]+)");

  String fileType;
  String fileName;
  DocumentAudienceType audience;

  public String getDocumentId() {
    String combinedId = this.audience.getValue() + "-" + this.fileType + "-" + this.fileName;
    return Base64.getEncoder().encodeToString(combinedId.getBytes());
  }

  public static DocumentsKey fromString(final String fileTyp) {
    var decodedKey = new String(Base64.getDecoder().decode(fileTyp));
    var matcher = pattern.matcher(decodedKey);

    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid Document Id");
    }

    var audience = DocumentAudienceType.fromValue(matcher.group("audience").toLowerCase());
    var fileType = matcher.group("fileType");
    var fileName = matcher.group("fileName");

    return new DocumentsKey(fileType, fileName, audience);
  }
}
