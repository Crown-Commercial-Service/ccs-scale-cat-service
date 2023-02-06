package uk.gov.crowncommercial.dts.scale.cat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "config.flags.experimental", ignoreUnknownFields = true)
@Data
public class ExperimentalFlagsConfig {
    private boolean asyncExecutorEnabled = true;
    private int asyncJaggaerSupplierCountThreshold = 200;
    private boolean asyncMissedJobsLoader = true;
    private boolean asyncOrphanJobsLoader = true;
    private boolean asyncResumeJobsOnStartup = true;
}
