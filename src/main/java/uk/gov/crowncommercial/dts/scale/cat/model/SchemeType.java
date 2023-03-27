package uk.gov.crowncommercial.dts.scale.cat.model;

public enum SchemeType {
    DUNS("duns"),COH("coh"), VAT("vat"), NHS("nhs");

    private String type;
    private SchemeType(String type){
        this.type = type;
    }

    public static SchemeType of(String type){
        if(null != type) {
            for (SchemeType t : values()) {
                if (t.type.equals(type))
                    return t;
            }
        }
        return null;
    }
}
