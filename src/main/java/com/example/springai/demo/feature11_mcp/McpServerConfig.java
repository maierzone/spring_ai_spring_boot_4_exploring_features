package com.example.springai.demo.feature11_mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider inventoryToolCallbacks(McpInventoryTools inventoryTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(inventoryTools)
                .build();
    }
}
