package com.ffreader.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    void sizesThePoolFromProperties() {
        ThreadPoolTaskExecutor executor = executor(new FfreaderProperties.Scraper(true, 30, 4));
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(4);
            assertThat(executor.getMaxPoolSize()).isEqualTo(8);
            assertThat(executor.getQueueCapacity()).isEqualTo(50);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("fanfic-parse-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void fallsBackToThreeThreadsWhenThePoolSizeIsNotPositive() {
        ThreadPoolTaskExecutor executor = executor(new FfreaderProperties.Scraper(true, 30, 0));
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void fallsBackToThreeThreadsWithoutScraperSettings() {
        ThreadPoolTaskExecutor executor = executor(null);
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
        } finally {
            executor.shutdown();
        }
    }

    private static ThreadPoolTaskExecutor executor(FfreaderProperties.Scraper scraper) {
        return (ThreadPoolTaskExecutor) new AsyncConfig()
                .fanficTaskExecutor(new FfreaderProperties(scraper, null, null));
    }
}
