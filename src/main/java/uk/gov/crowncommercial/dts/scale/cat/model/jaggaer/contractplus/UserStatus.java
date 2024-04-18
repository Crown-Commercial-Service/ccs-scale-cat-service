package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.contractplus;

public enum UserStatus {
    ACTIVE("Active"), INACTIVE("Inactive"), REJECTED("Rejected"), PENDING("Pending");
    private String value;
    UserStatus(String value) {
        this.value = value;
    }
    public String getValue() {
        return this.value;
    }
    public static UserStatus fromValue(String value) {
        for (UserStatus b : UserStatus.values()) {
            if (b.value == value) {
                return b;
            }
        }
        return null;
    }
}