package uk.gov.crowncommercial.dts.scale.cat.config.datasource;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;


@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "uk.gov.crowncommercial.dts.scale.agreement.*",
        entityManagerFactoryRef = "agreementEntityManagerFactory",
        transactionManagerRef = "agreementTransactionManager"
)
@RequiredArgsConstructor
public class AgreementDBConfiguration {
    @Bean
    @ConfigurationProperties(prefix="spring.agreementsource")
    public DataSource agreementDataSource(@Qualifier("agreementDataSourceProperties") DataSourceProperties primaryDataSourceProperties) {
        return primaryDataSourceProperties.initializeDataSourceBuilder().create().build();
    }


    @Bean(name = "agreementDataSourceProperties")
    @ConfigurationProperties("spring.agreementsource")
    public DataSourceProperties agreementDataSourceProperties() {
        return new DataSourceProperties();
    }


    @Bean(name = "agreementEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean
    entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("agreementDataSource") DataSource dataSource) {
        return builder.dataSource(dataSource).packages("uk.gov.crowncommercial.dts.scale.agreement.model").persistenceUnit("agreement").build();
    }

    @Bean(name = "agreementTransactionManager")
    public PlatformTransactionManager customerTransactionManager(
            @Qualifier("agreementEntityManagerFactory") EntityManagerFactory customerEntityManagerFactory ) {
        return new JpaTransactionManager(customerEntityManagerFactory);
    }
}
