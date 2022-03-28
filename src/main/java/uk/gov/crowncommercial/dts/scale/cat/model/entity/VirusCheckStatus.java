package uk.gov.crowncommercial.dts.scale.cat.model.entity;

/**
 * Virus check (document upload service) processing status
 */
public enum VirusCheckStatus {

  PROCESSING, SAFE, UNSAFE;

  public static VirusCheckStatus fromName(final String name) {
    for (VirusCheckStatus b : VirusCheckStatus.values()) {
      if (b.name().equalsIgnoreCase(name)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected name '" + name + "'");
  }

}
