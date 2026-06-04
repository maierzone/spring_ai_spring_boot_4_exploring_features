package com.example.springai.demo.feature18_docsrag;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FEATURE 18 – aktiviert das Spring-Scheduling (für {@link DocsRagWorker#tick()})
 * ausschließlich unter dem Profil {@code specs}. Damit bleibt in Default-/Test-
 * Läufen kein Scheduler-Thread aktiv.
 */
@Configuration
@Profile("specs")
@EnableScheduling
public class DocsRagConfig {
}
