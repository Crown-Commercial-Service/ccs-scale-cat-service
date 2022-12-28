package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class EnvironmentConfig {

    private String serviceInstance;

    public EnvironmentConfig(Environment environment){
        String instance = environment.getProperty("CF_INSTANCE_GUID", "instance");
        String index = environment.getProperty("CF_INSTANCE_INDEX", "0");
        serviceInstance = instance + "-" + index;
    }
    public String getServiceInstance(){
        return serviceInstance;
    }
}
