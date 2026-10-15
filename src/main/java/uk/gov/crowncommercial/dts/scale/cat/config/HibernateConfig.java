package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hibernate configuration to force JDBC to use UTC.
 */
@Configuration
public class HibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        // This programmatically forces the JDBC driver to use UTC,
        return hibernateProperties -> hibernateProperties.put("hibernate.jdbc.time_zone", "UTC");
    }
}