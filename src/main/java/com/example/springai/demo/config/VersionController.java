package com.example.springai.demo.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {
    @GetMapping("/version")
    public String getSpringBootVersion() {
        return org.springframework.boot.SpringBootVersion.getVersion();    }
}
