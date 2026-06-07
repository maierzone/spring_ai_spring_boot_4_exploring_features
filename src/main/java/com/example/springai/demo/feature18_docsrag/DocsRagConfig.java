package com.example.springai.demo.feature18_docsrag;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;


@Configuration
@Profile("specs")
@EnableScheduling
public class DocsRagConfig {
}
