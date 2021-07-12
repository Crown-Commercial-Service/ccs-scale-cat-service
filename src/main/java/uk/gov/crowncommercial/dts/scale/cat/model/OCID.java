package uk.gov.crowncommercial.dts.scale.cat.model;

import java.util.regex.Pattern;
import lombok.Value;

/**
 * Wrapper for Open Contracting Identifier values
 */
@Value
public class OCID {

  public static final Pattern pattern =
      Pattern.compile("(?<authority>[A-Za-z]+)-(?<prefix>[A-Za-z0-9]{6})-(?<id>\\S+)");

  String authority; // 'OCDS'
  String publisherPrefix;
  String internalId;

  public static OCID fromString(String ocid) {
    var matcher = pattern.matcher(ocid);

    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid OCID string");
    }

    var authority = matcher.group("authority");
    var publisherPrefix = matcher.group("prefix");
    var internalId = matcher.group("id");

    return new OCID(authority, publisherPrefix, internalId);
  }

}
