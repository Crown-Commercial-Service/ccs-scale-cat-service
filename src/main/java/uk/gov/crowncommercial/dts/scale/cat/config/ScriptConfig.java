package uk.gov.crowncommercial.dts.scale.cat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.util.Date;

@Configuration
@ConfigurationProperties(prefix = "script", ignoreUnknownFields = true)
@Data
public class ScriptConfig {
    private String dataFolder;

    private String currentFolder;

    public String getCurrentFolder(){
        if(null != currentFolder)
            return currentFolder;

        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        currentFolder =  dataFolder + format.format(new Date()) +"/";
        return currentFolder;
    }

    public String getBaseFolder(){
        return dataFolder + "base/";
    }
}
