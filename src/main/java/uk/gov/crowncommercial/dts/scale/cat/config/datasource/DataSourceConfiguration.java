package uk.gov.crowncommercial.dts.scale.cat.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;


public class DataSourceConfiguration {

    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }


}
