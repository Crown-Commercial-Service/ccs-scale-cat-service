package uk.gov.crowncommercial.dts.scale.cat;

import org.apache.commons.text.similarity.JaroWinklerDistance;

public class Util {

    private static JaroWinklerDistance winklerDistance = new JaroWinklerDistance();

    public static String getEntityId(String entityCode){
        if(null == entityCode)
            return null;
        if(entityCode.length() >= 9){
            return entityCode;
        }
        else
            return prefix(entityCode, 9);
    }

    private static String prefix(String entityCode, int i) {
        switch (i) {
            case 9:
                return ("000000000" + entityCode).substring(entityCode.length());
            case 8:
                return ("00000000" + entityCode).substring(entityCode.length());
            default:
                return entityCode;
        }
    }

    public static boolean isCohEqual(String houseNumber, String coh) {
        if(null == houseNumber || null == coh)
            return false;
        return getCoHId (houseNumber).equalsIgnoreCase(getCoHId(coh));

    }

    public static Double getSimilarity(String source, String target) {
        if(null == target || null == source)
            return 0d;

        String sourceName = source.toLowerCase().replace("limited", "").replace("ltd", "").trim();
        String targetName = target.toLowerCase().replace("limited", "").replace("ltd", "").trim();
        if(sourceName.contains("consultancy") && targetName.contains("consultancy")){
            sourceName = sourceName.replace("consultancy", "").trim();
            targetName = targetName.replace("consultancy", "").trim();
        }

        if(sourceName.contains("software") && targetName.contains("software")){
            sourceName = sourceName.replace("software", "").trim();
            targetName = targetName.replace("software", "").trim();
        }

        if (sourceName.equalsIgnoreCase(targetName))
            return 1d;
        return winklerDistance.apply(sourceName, targetName);
    }

    public static String getCoHId(String cohId) {
        if(null == cohId)
            return null;
        if(cohId.length() >= 8){
            return cohId;
        }
        else
            return prefix(cohId, 8);
    }
}
