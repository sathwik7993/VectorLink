package com.shortener.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

@Configuration
public class AsyncConfig {

    private static final Logger LOGGER = Logger.getLogger(AsyncConfig.class.getName());

    /**
     * Executor for click tracking (geo-IP lookup + Kafka publish), kept off the redirect request thread.
     * The queue is bounded; under extreme overload new clicks are dropped (and counted) rather than
     * blocking redirects.
     */
    @Bean(name = "clickTrackingExecutor")
    public Executor clickTrackingExecutor(MeterRegistry meterRegistry) {
        Counter rejected = Counter.builder("analytics.events.rejected")
                .description("Click events dropped because the click tracking executor queue was full")
                .register(meterRegistry);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(20000);
        executor.setThreadNamePrefix("click-tracker-");
        executor.setRejectedExecutionHandler((task, pool) -> {
            rejected.increment();
            LOGGER.warning("Click tracking queue full; dropping click event.");
        });
        executor.initialize();
        return executor;
    }
}
