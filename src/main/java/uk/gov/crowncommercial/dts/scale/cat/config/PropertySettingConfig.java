package uk.gov.crowncommercial.dts.scale.cat.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration component to establish system properties needed by the application
 */
@Configuration
public class PropertySettingConfig {
    /**
     * Setup any properties we need the system to be aware of
     */
    @PostConstruct
    public void setProperty() {
        System.setProperty("java.awt.headless", "true");
    }
}