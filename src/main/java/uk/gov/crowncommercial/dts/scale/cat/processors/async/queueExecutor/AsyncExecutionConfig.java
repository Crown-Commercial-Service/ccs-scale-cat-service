package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncExecutionConfig {

    @Bean("comExecutor")
    public ThreadPoolTaskExecutor getJaggaerExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setThreadGroupName("JGR");
        executor.setQueueCapacity(128);
        executor.setRejectedExecutionHandler(new BlockCallerExecutionPolicy());
        executor.initialize();
        return executor;
    }
}
