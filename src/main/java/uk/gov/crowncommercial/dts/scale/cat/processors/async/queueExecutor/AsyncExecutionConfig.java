package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncExecutionConfig {
    public static final int poolSize = 4;
    @Bean("comExecutor")
    public ThreadPoolTaskExecutor getJaggaerExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setThreadGroupName("JGR");
        executor.setQueueCapacity(poolSize * 4);
        executor.setRejectedExecutionHandler(new BlockCallerExecutionPolicy());
        executor.initialize();
        return executor;
    }
}
