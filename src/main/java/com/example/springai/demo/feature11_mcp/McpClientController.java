package com.example.springai.demo.feature11_mcp;

import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpClientController {

    private final List<McpSyncClient> mcpClients;
    private final ChatClient.Builder chatClientBuilder;

    /**
     * {@code List<McpSyncClient>} ist eine Collection-Injection: Gibt es keine
     * konfigurierten MCP-Server, injiziert Spring eine leere Liste (kein Fehler).
     */
    public McpClientController(List<McpSyncClient> mcpClients, ChatClient.Builder chatClientBuilder) {
        this.mcpClients = mcpClients;
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * Zeigt, welche Tools die angebundenen MCP-Server anbieten – reine Introspektion,
     * ohne Modell-/API-Key. Ohne konfigurierte Server kommt ein Hinweis zurueck.
     */
    @GetMapping("/api/mcp/client/tools")
    public Object discoverRemoteTools() {
        if (mcpClients.isEmpty()) {
            return "Kein MCP-Server konfiguriert. Siehe docs/HANDBUCH.md "
                    + "(spring.ai.mcp.client.*), um z.B. einen Loopback auf den eigenen Server zu setzen.";
        }
        return mcpClients.stream()
                .flatMap(client -> client.listTools().tools().stream())
                .map(tool -> tool.name() + " – " + tool.description())
                .toList();
    }

    /**
     * Laesst das Modell die remote angebotenen MCP-Tools nutzen. Funktioniert nur mit
     * konfiguriertem MCP-Server <em>und</em> gueltigem API-Key.
     */
    @GetMapping("/api/mcp/client/ask")
    public String askWithRemoteTools(
            @RequestParam(defaultValue = "Wie viele Monitore sind auf Lager?") String message) {
        if (mcpClients.isEmpty()) {
            return "Kein MCP-Server konfiguriert – es stehen keine remote Tools zur Verfuegung.";
        }
        ToolCallback[] remoteTools = new SyncMcpToolCallbackProvider(mcpClients).getToolCallbacks();
        return chatClientBuilder.build()
                .prompt()
                .user(message).tools(remoteTools)
                .call()
                .content();
    }
}
