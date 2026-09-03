package com.rai.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 판정처럼 202 로 응답하고 뒤에서 처리하는 작업용 스레드 풀. */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("assessmentTaskExecutor")
    public Executor assessmentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("assessment-");
        executor.initialize();
        return executor;
    }
}
