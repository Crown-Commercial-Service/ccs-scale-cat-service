package uk.gov.crowncommercial.dts.scale.cat.model.entity;


public enum AsyncPublishedStatus {

  IN_FLIGHT, SCHEDULED, COMPLETED, FAILED;

  public static AsyncPublishedStatus fromName(final String name) {
    for (AsyncPublishedStatus b : AsyncPublishedStatus.values()) {
      if (b.name().equalsIgnoreCase(name)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected name '" + name + "'");
  }

}
