package com.example.proyecto2025_BE.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Permite el envío de emails en un hilo aparte (@Async) para no bloquear la respuesta.
 * También define los pools acotados que usan los jobs de generación desde archivo.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "documentGenerationExecutor")
    public ThreadPoolTaskExecutor documentGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("doc-generation-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "documentSummaryExecutor", destroyMethod = "shutdown")
    public ExecutorService documentSummaryExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("doc-summary-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }
}
