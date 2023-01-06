package uk.gov.crowncommercial.dts.scale.cat;

public class Util {
    public static String getEntityId(String entityCode){
        if(entityCode.length() >= 9){
            return entityCode;
        }
        else
            return prefix(entityCode, 9);
    }

    private static String prefix(String entityCode, int i) {
        return ("000000000" + entityCode).substring(entityCode.length());
    }
}
