package uk.gov.crowncommercial.dts.scale.cat.csvreader;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CiiSingleOrg {
    private String scheme, schemeId;
    private String orgName, orgId;

    public String getDuns(){
        if(null != scheme && scheme.contains("US-DUN"))
            return trim(schemeId);

        return null;
    }

    public String getCoH(){
        if(null != scheme && !scheme.contains("US-DUN"))
            return trim(schemeId);

        return null;
    }

    private String trim(String input){
        if(null == input)
            return input;
        if(input.startsWith("'"))
            return input.replaceAll("'", "");
        return input;
    }
}
