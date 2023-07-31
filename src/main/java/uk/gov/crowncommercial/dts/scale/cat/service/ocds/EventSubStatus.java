package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

public enum EventSubStatus {

  AWAITING_OUTCOME("awaiting outcome"), AWARDED("awarded"), CANCELLED("cancelled");

  private String value;

  EventSubStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return this.value;
  }

  public static EventSubStatus fromValue(String value) {
    for (EventSubStatus b : EventSubStatus.values()) {
      if (b.value == value) {
        return b;
      }
    }
    return null;
  }

}
