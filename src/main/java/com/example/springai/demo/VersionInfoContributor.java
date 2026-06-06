package com.example.springai.demo;

import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class VersionInfoContributor implements InfoContributor {

    private final Environment env;

    VersionInfoContributor(Environment env) {
        this.env = env;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("spring-boot", SpringBootVersion.getVersion());
        versions.put("spring-ai", env.getProperty("info.versions.spring-ai"));
        versions.put("java", env.getProperty("info.versions.java"));
        builder.withDetail("versions", versions);
    }
}
