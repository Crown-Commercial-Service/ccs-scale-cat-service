package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

public enum RPAProcessNameEnum {

	BUYER_MESSAGING("BuyerMessaging"),

	AWARDING("Awarding"),

	END_EVALUATION("EndEvaluation"),

	ASSIGN_SCORE("AssignScore"),

	OPEN_ENVELOPE("OpenEnvelope");

	private String value;

	RPAProcessNameEnum(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	@Override
	public String toString() {
		return String.valueOf(value);
	}

	public static RPAProcessNameEnum fromValue(String value) {
		for (RPAProcessNameEnum b : RPAProcessNameEnum.values()) {
			if (b.value.equals(value)) {
				return b;
			}
		}
		throw new IllegalArgumentException("Unexpected value '" + value + "'");
	}
}