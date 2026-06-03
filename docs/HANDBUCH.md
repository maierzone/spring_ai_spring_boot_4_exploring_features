# Spring AI – Vertieftes Handbuch (Boot 4 / Spring AI 2.0.0-M-Linie)

Dieses Handbuch geht über die „Top 10" der README hinaus und behandelt vier
Themenblöcke, die in produktiven Spring-AI-Anwendungen den Unterschied zwischen
„Prototyp" und „betreibbar" ausmachen:

| # | Feature | Paket | Endpunkte |
|---|---------|-------|-----------|
| 11 | **MCP** – Model Context Protocol (Server **und** Client) | `feature11_mcp` | MCP-SSE-Endpunkt · `GET /api/mcp/client/tools` · `GET /api/mcp/client/ask` |
| 12 | **Observability** – Micrometer-Metriken & Tracing | `feature12_observability` | `GET /api/observability/ask` · `GET /api/observability/metrics` |
| 13 | **Evaluation** – LLM-as-a-Judge gegen Halluzinationen | `feature13_evaluation` | `GET /api/evaluate/relevancy` |
| 17 | **pgvector** – persistenter VectorStore (PostgreSQL) | `feature17_pgvector` | `GET /api/pgvector/info` · `POST /api/pgvector/documents` · `GET /api/pgvector/search` |

Schreibstil: praxisnah für Backend-Entwickler – kurzes Konzept, dann Code,
Konfiguration, `curl`-Beispiele und die Fallstricke, die in der Praxis weh tun.

> **Versionshinweis:** Der Code läuft auf **Spring Boot 4.0.6** mit **Spring AI
> 2.0.0-M8** (erste Boot-4-Linie). Die hier gezeigten APIs entsprechen
> funktional dem GA-Stand von Spring AI 1.1; einzelne Paketpfade können sich in
> einer späteren GA der 2.0-Linie noch verschieben.

---

## Feature 11 – Model Context Protocol (MCP)

### Worum geht es?

Tool Calling (Feature 6) macht Java-Methoden *innerhalb* einer Anwendung für das
Modell aufrufbar. **MCP** hebt genau dieses Konzept auf eine **standardisierte,
prozess- und netzwerkübergreifende** Ebene: Ein **MCP-Server** veröffentlicht
Tools (sowie optional Ressourcen und Prompts) über ein offenes Protokoll; ein
**MCP-Client** (z. B. Claude Desktop, ein anderer Agent oder eine weitere
Spring-AI-App) bindet diese Tools ein, ohne ihren Code zu kennen.

Bild dazu:

```
   ┌─────────────────────┐         MCP (SSE / streamable HTTP)        ┌──────────────────┐
   │  Diese Spring-App    │  ◀───────────────────────────────────────▶ │  MCP-Client       │
   │  = MCP-SERVER        │   listTools()/callTool()                   │  (Claude Desktop, │
   │  veröffentlicht       │                                            │   anderer Agent)  │
   │  Inventory-Tools      │                                            └──────────────────┘
   └─────────────────────┘
```

### Teil A – MCP-Server: eigene Tools veröffentlichen

**Abhängigkeit** (`pom.xml`):

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

Der Server veröffentlicht **alle `ToolCallbackProvider`-Beans** aus dem Kontext
als MCP-Tools. Wir bauen einen solchen Provider aus den `@Tool`-Methoden des
`McpInventoryTools` (derselbe Fach-Service wie beim klassischen Tool Calling):

```java
// McpServerConfig.java
@Bean
public ToolCallbackProvider inventoryToolCallbacks(McpInventoryTools inventoryTools) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(inventoryTools)
            .build();
}
```

```java
// McpInventoryTools.java – eine dünne Fassade vor dem ProductCatalogService
@Tool(description = "Liefert den aktuellen Lagerbestand (Stueckzahl) eines Produkts ...")
public String getStock(@ToolParam(description = "Name des Produkts ...") String productName) { ... }
```

**Konfiguration** (`application.properties`):

```properties
spring.ai.mcp.server.name=spring-ai-demo-inventory
spring.ai.mcp.server.version=0.0.1
```

Beim Start protokolliert die Autokonfiguration `Registered tools: 2` – die App
läuft jetzt zugleich als MCP-Server über den WebMVC/SSE-Transport.

**Anbinden an Claude Desktop** (Beispiel `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "spring-ai-demo-inventory": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### Teil B – MCP-Client: externe Server nutzen

**Abhängigkeit:**

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

Der Client verbindet sich beim Start mit allen unter `spring.ai.mcp.client.*`
konfigurierten Servern und stellt deren Tools als `McpSyncClient`-Beans bereit.
**Wichtig für CI/Tests:** Ist – wie im Standard dieser Demo – *kein* Server
konfiguriert, bleibt der Client **inert** (keine Verbindung, leere Liste). Das
hält das Quality-Gate offline.

Der Controller demonstriert beide Richtungen:

```java
// Introspektion: welche Tools bietet der/die angebundene(n) Server? (ohne API-Key)
@GetMapping("/api/mcp/client/tools")
public Object discoverRemoteTools() {
    if (mcpClients.isEmpty()) return "Kein MCP-Server konfiguriert ...";
    return mcpClients.stream()
            .flatMap(c -> c.listTools().tools().stream())
            .map(t -> t.name() + " – " + t.description())
            .toList();
}

// Das Modell die remote Tools nutzen lassen (API-Key nötig)
@GetMapping("/api/mcp/client/ask")
public String askWithRemoteTools(@RequestParam String message) {
    ToolCallback[] remoteTools = new SyncMcpToolCallbackProvider(mcpClients).getToolCallbacks();
    return chatClientBuilder.build().prompt().user(message).toolCallbacks(remoteTools).call().content();
}
```

**Loopback ausprobieren** (App muss laufen): den eigenen Server als Client-Ziel
eintragen –

```properties
spring.ai.mcp.client.sse.connections.self.url=http://localhost:8080
```

Danach listet `GET /api/mcp/client/tools` die `getStock`/`listProducts`-Tools –
jetzt als *remote* Tools.

### Sicherheit & Fallstricke

- **Server + Client in einer App** ist ungewöhnlich (hier nur zu Demozwecken).
  Standardmäßig veröffentlicht der Server **keine** über den Client bezogenen
  Tools weiter (Log: *„MCP Client tools will not be exposed…"*). Erst
  `spring.ai.mcp.server.expose-mcp-client-tools=true` ändert das – mit Bedacht
  einsetzen (Tool-Loops vermeiden).
- **Vertrauen:** Ein MCP-Server gibt Tools an *fremde* Clients. Beschreibe Tools
  präzise, validiere Parameter und exponiere nur, was nach außen darf – ein Tool
  ist ausführbarer Code.
- **Authentifizierung:** Für nicht-lokale Server SSE/streamable HTTP **mit
  OAuth2** absichern (Transport-Customizer). Niemals ungeschützt ins Netz.
- **Transport:** `…-webmvc` (Servlet/SSE) vs. `…-webflux` (reaktiv) – passend
  zum App-Stack wählen. Diese App ist Servlet-basiert.

---

## Feature 12 – Observability (Micrometer)

### Worum geht es?

Sobald **Spring Boot Actuator** (und damit Micrometer) auf dem Klassenpfad liegt,
instrumentiert Spring AI seine Aufrufe **automatisch** – ohne Codeänderung. Das
ist der produktive Gegenpol zum selbstgebauten `MetricsLoggingAdvisor` (Feature
10): Statt Latenz/Tokens nur ins Log zu schreiben, landen sie **standardisiert**
in der Metrik-Registry und sind via `/actuator`, Prometheus oder Grafana
auswertbar.

**Abhängigkeit:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Konfiguration:**

```properties
management.endpoints.web.exposure.include=health,info,metrics
```

### Was wird erfasst?

| Typ | Name | Inhalt |
|-----|------|--------|
| Metrik (Timer) | `gen_ai.client.operation` | Latenz der Modelloperationen, getaggt nach Modell/Operation |
| Metrik (Counter) | `gen_ai.client.token.usage` | verbrauchte Tokens (Input / Output / Total) |
| Observation | `spring.ai.chat.client` | umschließt jeden `call()`/`stream()`; trägt Advisor-Namen, Tool-Namen, Conversation-ID |

Der Endpunkt `GET /api/observability/metrics` liest die `gen_ai`-/`spring.ai`-
Meter direkt aus der Registry – vor dem ersten Modellaufruf erwartungsgemäß leer:

```java
for (Meter meter : meterRegistry.getMeters()) {
    String name = meter.getId().getName();
    if (name.startsWith("gen_ai") || name.startsWith("spring.ai")) {
        for (Measurement m : meter.measure()) {
            result.put(name + " [" + m.getStatistic() + "]", m.getValue());
        }
    }
}
```

Nach einem echten Aufruf (`GET /api/observability/ask?...` mit gültigem API-Key)
erscheinen die Token-Zähler auch unter
`GET /actuator/metrics/gen_ai.client.token.usage`.

### Tracing & Prompt-Inhalte

- **Verteiltes Tracing:** zusätzlich einen Micrometer-Tracing-Bridge (z. B.
  `micrometer-tracing-bridge-brave`) + Exporter (Zipkin/OTLP) einbinden; die
  `spring.ai.chat.client`-Observations propagieren dann Trace-/Span-IDs über
  Advisor-Ketten und Tool-Aufrufe hinweg.
- **Prompt/Completion im Trace:** Standardmäßig **nicht** exportiert
  (Datenschutz). Nur bewusst und kurzfristig aktivieren:

  ```properties
  spring.ai.chat.client.observations.log-prompt=true
  spring.ai.chat.client.observations.log-completion=true
  ```

### Fallstricke

- **Kosten-Tag-Kardinalität:** Prompt-Inhalte als High-Cardinality-Tags können
  die Metrik-Registry sprengen – deshalb sind sie per Default aus.
- **Token-Metriken** erscheinen erst **nach** dem ersten erfolgreichen Aufruf;
  ein leeres `/api/observability/metrics` bei Kaltstart ist korrekt.
- Nicht jeder Provider liefert vollständige Usage-Daten – für Anthropic/Claude
  sind Input-/Output-Tokens vorhanden.

---

## Feature 13 – Evaluation (LLM-as-a-Judge)

### Worum geht es?

Wie testet man eine **nicht-deterministische** KI-Antwort? Spring AI liefert
`Evaluator`-Implementierungen, die ein (ggf. zweites) Modell als **Richter**
einsetzen:

- **`RelevancyEvaluator`** – ist die Antwort durch den mitgegebenen Kontext
  (z. B. RAG-Dokumente) **gedeckt** und relevant, statt frei halluziniert?
- **`FactCheckingEvaluator`** – wird eine Behauptung durch den Kontext gestützt?

Das ist der natürliche Abschluss einer RAG-Pipeline (Feature 7): erst Kontext
abrufen → antworten → **automatisiert bewerten** → als Qualitäts-Gate nutzen.

```java
// EvaluationController.java
this.relevancyEvaluator = RelevancyEvaluator.builder().chatClientBuilder(builder).build();

EvaluationRequest request = new EvaluationRequest(question, List.of(new Document(context)), answer);
EvaluationResponse response = relevancyEvaluator.evaluate(request);
boolean grounded = response.isPass();   // YES/NO-Urteil des Richter-Modells
```

`curl`:

```bash
curl "localhost:8080/api/evaluate/relevancy" \
  --data-urlencode "question=Was ist RAG?" \
  --data-urlencode "context=RAG kombiniert Retrieval mit Generation." \
  --data-urlencode "answer=RAG ruft passende Dokumente ab und nutzt sie als Kontext." -G
```

### Im Test ohne echtes Modell

Der Clou für CI: Der Richter ist austauschbar. In `RelevancyEvaluatorOfflineTest`
ersetzen wir ihn durch einen **Stub-`ChatModel`**, der deterministisch `YES`
bzw. `NO` liefert – so ist die *Verdrahtung* (Prompt → Urteil → `isPass()`)
offline und ohne API-Key prüfbar:

```java
ChatModel fixed = (Prompt p) -> new ChatResponse(List.of(new Generation(new AssistantMessage("YES"))));
var evaluator = RelevancyEvaluator.builder().chatClientBuilder(ChatClient.builder(fixed)).build();
assertThat(evaluator.evaluate(request).isPass()).isTrue();
```

In der echten CI würde stattdessen ein reales (günstiges) Modell als Richter
laufen und der Test als **Eval-Gate** über einem Golden-Dataset wachen.

### Fallstricke

- **Richter ≠ Autor:** Idealerweise bewertet ein *anderes*/stärkeres Modell als
  das, das die Antwort erzeugt hat – sonst bewertet sich das Modell selbst.
- **Kosten/Latenz:** Jede Evaluation ist ein zusätzlicher Modellaufruf. Für
  CI-Gates über kuratierte Golden-Sets einsetzen, nicht auf jedem Request.
- **Urteils-Parsing:** Der `RelevancyEvaluator` vergleicht die Modellantwort per
  `equalsIgnoreCase` gegen `YES`/`NO`. Ein eigener Prompt-Template muss dieses
  Ausgabeformat beibehalten.

---

## Feature 17 – pgvector (persistenter VectorStore)

### Worum geht es?

Die RAG-Demo (Feature 7) nutzt einen `SimpleVectorStore`: Embeddings liegen rein
im Arbeitsspeicher und sind nach einem Neustart verloren. Für „betreibbar" braucht
es einen persistenten Speicher. **pgvector** ist die PostgreSQL-Extension für
Vektor-Ähnlichkeitssuche; Spring AI bindet sie über den `PgVectorStore` an dasselbe
`VectorStore`-Interface an. Der Clou ist derselbe wie beim Chat-Memory (Feature 5):
**nur die Bean wird getauscht, der Anwendungscode bleibt unverändert.**

### Profil-Switch statt Code-Umbau

Beide Implementierungen erfüllen das `VectorStore`-Interface. Welche aktiv ist,
entscheidet das Spring-Profil:

```java
// config/DemoBeans.java
@Bean
@Profile("!pgvector")                       // entfällt in der Standardlaufzeit
public VectorStore vectorStore(EmbeddingModel embeddingModel) {
    SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
    store.add(KnowledgeLoader.loadParagraphs(faq));
    return store;
}
```

- **Standardlaufzeit** (`spring.profiles.active=pgvector` in `application.properties`):
  Die obige Bean entfällt. Die Spring-AI-Autokonfiguration ist
  `@ConditionalOnMissingBean(VectorStore.class)` und steuert nun den `PgVectorStore`
  bei – verdrahtet auf die ohnehin laufende PostgreSQL.
- **Tests** (`@ActiveProfiles("test")`): Der offline `SimpleVectorStore` (H2) bleibt
  aktiv; die pgvector-Autokonfiguration ist im `test`-Profil ausgeschlossen (siehe
  Fallstricke). So bleibt das Quality-Gate ohne Docker grün.

### Konfiguration

`pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
```

`application-pgvector.properties` (nur im Profil `pgvector` geladen):

```properties
spring.ai.vectorstore.pgvector.initialize-schema=true   # Extension + Tabelle + Index anlegen
spring.ai.vectorstore.pgvector.dimensions=256           # == HashingEmbeddingModel
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
```

`compose.yaml`: Das Image wechselt von `postgres:17` auf `pgvector/pgvector:pg17`
(funktional identisch, bringt aber die Extension `vector` mit). Chat-Memory und die
eGK-Tabellen laufen auf demselben Container unverändert weiter.

### Was der Endpunkt zeigt

```bash
# Welche VectorStore-Implementierung ist aktiv? (PgVectorStore zur Laufzeit)
curl "localhost:8080/api/pgvector/info"

# Dokument mit Metadatum ablegen – persistiert in der Tabelle vector_store
curl -X POST "localhost:8080/api/pgvector/documents?text=Spring+AI+vereinfacht+RAG&category=spring"

# Ähnlichkeitssuche mit DB-seitigem Metadaten-Filter
curl -G "localhost:8080/api/pgvector/search" \
     --data-urlencode "query=Wie greife ich auf ein LLM zu?" \
     --data-urlencode "category=spring"
```

Die beiden pgvector-Stärken werden damit greifbar: **Persistenz** (die abgelegten
Dokumente überleben einen Neustart, weil sie in PostgreSQL liegen) und der
**Metadaten-Filter** (`category == '…'`), den pgvector direkt in der Datenbank
auswertet.

### Fallstricke

- **Dimension muss passen:** `pgvector.dimensions` muss exakt der Ausgabe des
  `EmbeddingModel` entsprechen (hier 256). Weicht sie ab, scheitert das Einfügen.
- **Extension braucht das richtige Image:** Das offizielle `postgres`-Image kennt
  `CREATE EXTENSION vector` nicht – daher `pgvector/pgvector`.
- **Bean-Namens-Kollision in Tests:** Die pgvector-Autokonfiguration steuert
  ebenfalls eine Bean namens `vectorStore` bei. Liegt sie im Test-Classpath neben
  der eigenen `DemoBeans`-Bean, scheitert der Kontext mit
  `BeanDefinitionOverrideException` (Override ist in Boot deaktiviert). Lösung:
  im `test`-Profil ausschließen –
  `spring.autoconfigure.exclude=…pgvector.autoconfigure.PgVectorStoreAutoConfiguration`.

---

## Ausblick – weitere lohnende Spring-AI-Themen

Kandidaten für eine nächste Ausbaustufe dieses Handbuchs:

- **Prompt Caching (Anthropic/Bedrock):** bis zu ~90 % Kostenersparnis und
  schnellere Antworten durch 5-Min-/1-Std-Cache wiederkehrender Prompt-Präfixe.
- **Agentic Workflows / rekursive Advisors:** Chaining, Routing und
  Evaluator-Optimizer-Muster; ein Advisor ruft andere Advisors in mehrstufigen
  Abläufen auf (u. a. Grundlage für LLM-as-a-Judge im Loop).
- **ETL-/Document-Pipeline:** `DocumentReader`/`TokenTextSplitter`/
  `MetadataEnricher` für das Befüllen des Vektorspeichers aus PDFs/HTML.
- **Moderation & Guardrails:** Moderation-API und PII-/Safety-Advisors über die
  einfache `SafeGuardAdvisor`-Sperrliste (Feature 10) hinaus.
- **Bild- & Audio-Modelle:** `ImageModel` (Generierung), Transkription/TTS.
- **Resilienz:** Retry/Rate-Limiting und Timeouts um Modellaufrufe.
