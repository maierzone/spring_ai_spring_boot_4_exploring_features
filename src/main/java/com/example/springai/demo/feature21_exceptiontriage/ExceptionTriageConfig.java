package com.example.springai.demo.feature21_exceptiontriage;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Aktiviert die asynchrone Verarbeitung fuer die Fehler-Triage und stellt einen
 * <em>eigenen, beschraenkten</em> Thread-Pool bereit.
 *
 * <p>Der Pool ist klein und die Queue endlich – bei Ueberlast wird die zusaetzliche
 * Analyse-Arbeit <em>verworfen</em> ({@link ThreadPoolExecutor.DiscardPolicy}) statt
 * den aufrufenden Request-Thread zu blockieren. Lastabwurf ist hier die richtige
 * Wahl: eine ausgefallene Triage ist folgenlos (Fallback greift), ein blockierter
 * Request-Thread waere ein zweites Problem.</p>
 */
@Configuration
@EnableAsync
public class ExceptionTriageConfig {

    @Bean("triageExecutor")
    public Executor triageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-triage-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
