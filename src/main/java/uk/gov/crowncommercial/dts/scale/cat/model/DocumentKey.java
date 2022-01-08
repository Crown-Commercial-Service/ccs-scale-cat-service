package uk.gov.crowncommercial.dts.scale.cat.model;

import java.util.Base64;
import java.util.regex.Pattern;
import lombok.Value;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;

/**
 * Helper class to manage Document IDs.
 *
 * A Document ID is a base64 encoded combination of fileId, fileName and audience. This information
 * is required to retrieve a document from Jaggaer, and is encapsulated in the Document ID value.
 *
 * A Document Key is an object that wraps the Document ID and allows each value to be easily
 * accessed internally in the Tenders API.
 *
 */
@Value
public class DocumentKey {

  public static final Pattern pattern =
      Pattern.compile("(?<audience>[A-Za-z]+)-(?<fileId>[0-9]+)-(?<fileName>[\\s\\S]+)");

  // The Jaggaer internal file id
  Integer fileId;
  String fileName;
  DocumentAudienceType audience;

  public String getDocumentId() {
    String combinedId = this.audience.getValue() + "-" + this.fileId + "-" + this.fileName;
    return Base64.getEncoder().encodeToString(combinedId.getBytes());
  }

  public static DocumentKey fromString(final String documentId) {
    var decodedKey = new String(Base64.getDecoder().decode(documentId));
    var matcher = pattern.matcher(decodedKey);

    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid Document Id");
    }

    var audience = DocumentAudienceType.fromValue(matcher.group("audience").toLowerCase());
    var fileId = Integer.valueOf(matcher.group("fileId"));
    var fileName = matcher.group("fileName");

    return new DocumentKey(fileId, fileName, audience);
  }
}
