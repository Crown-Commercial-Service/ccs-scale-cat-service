package uk.gov.crowncommercial.dts.scale.cat.csvreader;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CiiOrg {
    private String scheme1, scheme1Id, scheme2, scheme2Id;
    private String orgName, orgId;

    public String getDuns(){
        if(null != scheme1 && scheme1.contains("US-DUN"))
            return trim(scheme1Id);
        else if(null != scheme2  && scheme2.contains("US-DUN"))
            return trim(scheme2Id);

        return null;
    }

    public String getCoH(){
        if(null != scheme1 && scheme1.contains("GB-COH"))
            return trim(scheme1Id);
        else if(null != scheme2  && scheme2.contains("US-DUN"))
            return trim(scheme2Id);

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
