package com.example.springai.demo.feature11_mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FEATURE 11 (Teil A) – MCP-Server: Bereitstellung der Tools.
 *
 * <p>Der {@code spring-ai-starter-mcp-server-webmvc} veroeffentlicht alle
 * {@link ToolCallbackProvider}-Beans aus dem Kontext als MCP-Tools. Wir bauen einen
 * solchen Provider hier aus den {@link McpInventoryTools} (deren {@code @Tool}-
 * Methoden per Reflection eingelesen werden).</p>
 *
 * <p>Damit laeuft die Anwendung ohne weiteres Zutun als MCP-Server: Ein externer
 * Client verbindet sich auf den SSE-Transport (Standardpfad {@code /sse} bzw.
 * {@code /mcp/message}), erhaelt die Tool-Beschreibungen und kann die Methoden
 * aufrufen – exakt der Inhalt, den ein Modell sonst per Tool Calling nutzt, nur
 * eben standardisiert und prozessuebergreifend.</p>
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider inventoryToolCallbacks(McpInventoryTools inventoryTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(inventoryTools)
                .build();
    }
}
