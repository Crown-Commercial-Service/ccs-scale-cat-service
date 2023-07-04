package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Component;

/*
    Overrides the Spring Hibernate database version detection, and lets Hibernate do it itself - Spring will only use old versions
 */
@Component
@Primary
public class CustomHibernateJpaVendorAdaptor extends HibernateJpaVendorAdapter {
    @Override
    protected Class<?> determineDatabaseDialectClass(Database database) {
        return null;
    }
}