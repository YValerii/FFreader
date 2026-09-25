package com.ffreader.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated thread pool for fanfic parsing jobs so that several stories
 * can be scraped and converted in parallel without blocking HTTP request threads.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "fanficTaskExecutor")
    public Executor fanficTaskExecutor(FfreaderProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = properties.scraper() != null && properties.scraper().poolSize() > 0
                ? properties.scraper().poolSize()
                : 3;
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("fanfic-parse-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
