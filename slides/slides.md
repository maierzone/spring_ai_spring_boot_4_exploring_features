---
theme: seriph
title: Spring AI – Backend Working Group
info: |
  ## Spring AI – Kern-Features anhand des Demo-Projekts
  Begleitendes Foliendeck zum Repository spring_ai_spring_boot_4_exploring_features.
  Jede Feature-Folie verweist auf die konkrete Beispiel-Datei im Projekt.
class: text-center
highlighter: shiki
lineNumbers: false
drawings:
  persist: false
transition: slide-left
mdc: true
fonts:
  mono: 'JetBrains Mono'
---

# Spring AI

## KI-Features für Spring-Boot-Anwendungen — pragmatisch & idiomatisch

<div class="pt-8 opacity-80 text-sm">
Backend Working Group · Live-Demo am Code
</div>

<div class="abs-br m-6 flex gap-2 text-xs">
  <span class="px-2 py-1 rounded bg-white/15 text-white backdrop-blur">Spring Boot 4.0.6</span>
  <span class="px-2 py-1 rounded bg-white/15 text-white backdrop-blur">Spring AI 2.0.0-M8</span>
  <span class="px-2 py-1 rounded bg-white/15 text-white backdrop-blur">Java 21</span>
  <span class="px-2 py-1 rounded bg-white/15 text-white backdrop-blur">Anthropic Claude</span>
</div>

<style>
.slidev-layout.cover {
  background: linear-gradient(125deg, #0d1b2a 0%, #1b3a2f 55%, #1f6f43 100%) !important;
  background-image: linear-gradient(125deg, #0d1b2a 0%, #1b3a2f 55%, #1f6f43 100%) !important;
}
.slidev-layout.cover h1 { color: #ffffff; }
.slidev-layout.cover h2 { color: #d6f5e3; font-weight: 400; }
.slidev-layout.cover p { color: #c3d3df; }
</style>

<!--
Sprechernotiz: Ziel ist nicht „noch ein KI-Framework", sondern zu zeigen, wie sich
KI mit den vertrauten Spring-Mitteln (DI, Beans, Autoconfiguration, Tests) bauen lässt.
Jede Folie hat unten rechts den Dateipfad — von hier direkt nach IntelliJ springen.
-->

---
layout: two-cols-header
layoutClass: gap-8
---

# Was ist Spring AI?

::left::

Eine **Spring-idiomatische Abstraktion** über LLM-Provider — kein Bruch mit dem gewohnten Programmiermodell.

- 🧩 **Provider-agnostisch** — derselbe Code für Anthropic, OpenAI, Ollama …
- ⚙️ **Auto-Configuration** — Starter rein, `ChatClient.Builder` wird injiziert
- 🧠 **Bausteine** — Prompt Templates, Structured Output, Tools, Memory, Advisors
- 🍃 **Spring durch & durch** — Beans, DI, `@RestController`, Tests, CI

::right::

```java
// Starter genügt – Spring AI konfiguriert
// einen ChatClient.Builder als Bean.
@RestController
class HelloAi {
  private final ChatClient chat;

  HelloAi(ChatClient.Builder builder) {
    this.chat = builder.build();
  }

  @GetMapping("/hello")
  String hello() {
    return chat.prompt()
        .user("Sag Hallo auf Deutsch.")
        .call()
        .content();
  }
}
```

<div class="mt-3 text-xs opacity-70">
Das ist das gesamte Setup — keine SDK-Verdrahtung, kein Boilerplate.
</div>

---
layout: default
---

# Architektur — ein Modell, viele Bausteine

Alles läuft über den **ChatClient**. Querschnitts-Logik steckt in der **Advisor-Kette**, der Provider ist austauschbar.

```mermaid {scale: 0.72}
flowchart LR
  REQ["HTTP / Service"] --> CC["ChatClient<br/>(fluent API)"]
  CC --> ADV["Advisor-Kette"]
  ADV -->|Verlauf laden| MEM[("Chat Memory<br/>JDBC / PostgreSQL")]
  ADV --> MODEL["ChatModel<br/>Anthropic Claude"]
  MODEL -.->|ruft auf| TOOLS["@Tool<br/>Java-Methoden"]
  TOOLS -.->|Ergebnis| MODEL
  MODEL --> OUT["Antwort<br/>Text · Stream · typisiertes Objekt"]
```

<div class="mt-2 text-xs opacity-70 grid grid-cols-2 gap-x-8">
<div>📦 Ein Feature = ein Package: <code>com.example.springai.demo.featureNN_*</code></div>
<div>🔗 Heutiger Fokus: ChatClient · Prompt Templates · Structured Output · Tools · Memory · Agentic Patterns</div>
</div>

---
layout: two-cols-header
layoutClass: gap-8
---

# 1 · ChatClient — die zentrale API

::left::

Das Herzstück: Prompt zusammenbauen → ausführen → Antwort lesen.

- **Fluent**: `prompt()` → `user()` → `call()` → `content()`
- **Provider-Abstraktion** — Anthropic heute, OpenAI/Ollama ohne Code-Änderung
- Builder ist **autokonfiguriert** mit dem aktiven `ChatModel`
- `defaultSystem(...)` setzt **Standardverhalten** für alle Anfragen

<div class="mt-4 text-xs opacity-70">
📁 <code>feature01_chatclient/ChatClientController.java</code><br/>
🔗 <code>GET /api/chat?message=…</code>
</div>

::right::

```java {2-5,9-12}
public ChatClientController(ChatClient.Builder builder) {
  this.chatClient = builder
      .defaultSystem("Du bist ein hilfreicher "
          + "Assistent, antworte knapp auf Deutsch.")
      .build();
}

@GetMapping("/api/chat")
public String chat(@RequestParam String message) {
  return chatClient.prompt()
      .user(message)
      .call()        // synchron ausführen
      .content();    // reiner Antworttext
}
```

---
layout: two-cols-header
layoutClass: gap-8
---

# 2 · Prompt Templates — Vorlage statt String-Klebe

::left::

Prompts haben variable Anteile. `PromptTemplate` trennt **Vorlage** und **Werte** sauber.

- Platzhalter `{topic}` statt String-Verkettung
- Werte erst bei `render(Map)` eingesetzt
- Lesbarer, testbar, **weniger Prompt-Injection**
- Templates können auch aus Ressourcen-Dateien kommen

<div class="mt-4 text-xs opacity-70">
📁 <code>feature03_prompttemplate/PromptTemplateController.java</code><br/>
🔗 <code>GET /api/joke?topic=Katzen&language=Englisch</code>
</div>

::right::

```java {1-3,5-7,9-12}
PromptTemplate template = new PromptTemplate(
    "Erzaehle einen Witz ueber {topic}. "
    + "Antworte ausschliesslich auf {language}.");

String prompt = template.render(Map.of(
    "topic", topic,
    "language", language));

return chatClient.prompt()
    .user(prompt)
    .call()
    .content();
```

---
layout: two-cols-header
layoutClass: gap-8
---

# 3 · Structured Output — vom Freitext zum Java-Typ

::left::

`.entity(Class)` weist das Modell an, **JSON im Schema deines Records** zu liefern — und parst es direkt.

- Schema wird aus **Record + Enums** abgeleitet
- Enums erzwingen ein **geschlossenes Werteset**
- Killer-Use-Case: **unstrukturierten Input strukturieren** (Ticket → Kategorie/Priorität/Sentiment)
- Ergebnis ist typsicher → direkt in Routing/SQL nutzbar

<div class="mt-4 text-xs opacity-70">
📁 <code>feature04_structured/StructuredOutputController.java</code><br/>
🔗 <code>GET /api/recipe</code> · <code>POST /api/tickets/analyze</code>
</div>

::right::

```java {1,4-8}
record Recipe(String title, List<String> ingredients,
              List<String> steps) {}

return chatClient.prompt()
    .user("Erstelle ein Rezept fuer: " + dish)
    .call()
    .entity(Recipe.class);  // JSON-Schema +
                            // automatisches Parsing
```

```java {1-2}
enum Category { BUG, FEATURE_REQUEST, BILLING, OTHER }
record TicketAnalysis(Category category,
    Priority priority, Sentiment sentiment, String summary) {}
```

---
layout: two-cols-header
layoutClass: gap-8
---

# 4 · Tool Calling — das Modell ruft deinen Code

::left::

Das Modell darf während der Antwort **eigene Java-Methoden** aufrufen (aktuelle Daten, DB, APIs).

- `@Tool` / `@ToolParam` mit Beschreibungen
- Tool ist eine **dünne Fassade vor Geschäftslogik**
- Als Spring-Bean → **DI auf Services/Repositories**
- Modell **wählt selbst**, welches Tool (falls überhaupt)

<div class="mt-4 text-xs opacity-70">
📁 <code>feature06_tools/ProductTools.java</code> · <code>ToolCallingController.java</code><br/>
🔗 <code>GET /api/tools?message=Wie viele Monitore sind auf Lager?</code>
</div>

::right::

```java {1,3-6}
@Component
class ProductTools {
  @Tool(description = "Liefert den Lagerbestand "
      + "eines Produkts anhand seines Namens.")
  String getProductStock(
      @ToolParam(description="Produktname") String name) {
    return catalog.stockFor(name)...;
  }
}
```

```java {2}
chatClient.prompt().user(message)
    .tools(productTools, new DateTimeTools())
    .call().content();
```

---
layout: two-cols-header
layoutClass: gap-8
---

# 5 · Chat Memory — Gespräch mit Gedächtnis

::left::

Ein LLM ist **zustandslos**. Der `MessageChatMemoryAdvisor` lädt den Verlauf je **Konversations-ID** und hängt ihn an.

- Verlauf getrennt pro `conversationId`
- `MessageWindowChatMemory` = gleitendes Fenster (N Nachrichten)
- **JDBC-Repository** → überlebt einen Neustart (PostgreSQL)
- Repository austauschbar (H2 in Tests) — **Code bleibt gleich**

<div class="mt-4 text-xs opacity-70">
📁 <code>feature05_memory/ChatMemoryController.java</code> · <code>config/DemoBeans.java</code><br/>
🔗 <code>POST /api/memory/{conversationId}?message=…</code>
</div>

::right::

```java {3-4}
public ChatMemoryController(ChatClient.Builder b,
                            ChatMemory chatMemory) {
  this.chatClient = b.defaultAdvisors(
      MessageChatMemoryAdvisor.builder(chatMemory).build())
    .build();
}
```

```java {3}
chatClient.prompt().user(message)
    .advisors(a -> a.param(
        ChatMemory.CONVERSATION_ID, conversationId))
    .call().content();
```

---
layout: two-cols-header
layoutClass: gap-8
---

# Querschnitt: Advisors — der Erweiterungspunkt

::left::

Memory ist **selbst ein Advisor**. Die Kette umschließt jeden Call — ideal für Querschnittsbelange.

- **Interceptor-Muster** um Request/Response
- Eingebaut: `MessageChatMemoryAdvisor`, `SafeGuardAdvisor`, `SimpleLoggerAdvisor`
- **Eigene** Advisors: Metriken, Logging, Guardrails
- Frei kombinierbar via `defaultAdvisors(...)`

<div class="mt-4 text-xs opacity-70">
📁 <code>feature10_advisors/MetricsLoggingAdvisor.java</code> · <code>AdvisorController.java</code><br/>
🔗 <code>GET /api/advisors?message=…</code>
</div>

::right::

```mermaid {scale: 0.6}
flowchart TB
  R[Request] --> M[MetricsLogging]
  M --> L[SimpleLogger]
  L --> S[SafeGuard]
  S --> MODEL[ChatModel]
  MODEL --> S2[Response zurück durch die Kette]
```

```java
builder.defaultAdvisors(
    new MetricsLoggingAdvisor(),
    new SimpleLoggerAdvisor(),
    SafeGuardAdvisor.builder()
        .sensitiveWords(List.of("passwort")).build());
```

---
layout: two-cols-header
layoutClass: gap-8
---

# Agentic Pattern: Evaluator-Optimizer

::left::

Ein LLM verbessert seine Ausgabe in einer **Kritik-Schleife** — statt einmal zu raten, iteriert es bis ein Qualitätsmaßstab erfüllt ist.

- **Generator** erzeugt einen Vorschlag (z. B. SQL aus natürlicher Sprache)
- **Evaluator** prüft zweistufig: `SqlGuard` deterministisch → LLM-Richter
- Feedback fließt als Kontext in den **nächsten Versuch**
- Harte `maxIterations`-Grenze schützt vor Endlosschleifen
- `attempts`-Liste macht den Fortschritt sichtbar (v1 scheitert, v2 grün)

<div class="mt-4 text-xs opacity-70">
📁 <code>feature16_evaluator/SqlEvaluatorOptimizer.java</code><br/>
🔗 <code>GET /api/evaluator/sql?question=…</code>
</div>

::right::

```java
for (int i = 1; i <= maxIterations; i++) {
    sql = generator.generate(sql, feedback);
    Evaluation ev = evaluator.evaluate(sql);
    attempts.add(new Attempt(i, sql, ev));

    if (ev.valid()) return new Outcome(true, sql, attempts);
    feedback = ev.feedback(); // Kritik → nächster Versuch
}
return new Outcome(false, sql, attempts);
```

```json
{ "attempts": [
    { "iteration": 1,
      "evaluation": { "valid": false,
        "feedback": "Spalte 'alter' existiert nicht" }},
    { "iteration": 2,
      "evaluation": { "valid": true }}
]}
```

---
layout: two-cols-header
layoutClass: gap-8
---

# Live-Demo — am Projekt ausprobieren

::left::

**Starten** (PostgreSQL via Docker Compose automatisch):

```bash
export ANTHROPIC_API_KEY=sk-...
./mvnw spring-boot:run
```

**Demo-Console** im Browser:

```
http://localhost:8080
```

<div class="mt-3 text-xs opacity-70">
📁 <code>src/main/resources/static/index.html</code><br/>
Visualisiert Structured Output, Tool Calling & Agentic Patterns.
</div>

::right::

```bash
# 1 · ChatClient
curl "localhost:8080/api/chat?message=Erklaere+Spring+AI"

# 3 · Structured Output (Ticket → Typ)
curl -X POST localhost:8080/api/tickets/analyze \
  -d "App stuerzt beim Login ab, sehr aergerlich!"

# 4 · Tool Calling
curl "localhost:8080/api/tools?message=Monitore+auf+Lager?"

# Agentic Pattern – Evaluator-Optimizer
curl "localhost:8080/api/evaluator/sql?question=Versicherte+ueber+65?"
```

---
layout: default
class: text-left
---

# Key Takeaways

<div class="grid grid-cols-2 gap-x-10 gap-y-3 mt-4">

<div>

### Warum Spring AI?
- 🍃 **Idiomatisch** — DI, Beans, Autoconfig, Tests, CI wie gewohnt
- 🔌 **Provider-agnostisch** — Modell tauschen ohne Code-Umbau
- 🧱 **Komponierbar** — Tools, Memory, Advisors · Agentic Patterns
- 🚀 **Vom Snippet zur Produktion** — DB-Persistenz, Tests, CI im Repo

</div>

<div>

### Repo-Wegweiser
- 📦 Ein Package je Feature: <code>featureNN_*</code>
- 🧪 Tests laufen **offline** (H2, kein API-Key)
- 📖 <code>README.md</code> — Setup & alle Endpoints
- 🖥️ <code>static/index.html</code> — interaktive Console

</div>

</div>

<div class="abs-br m-6 text-xs opacity-60">
Repository: <code>spring_ai_spring_boot_4_exploring_features</code>
</div>

<div class="mt-8 text-center text-2xl opacity-90">
Fragen? → Wir springen direkt in den Code. 🛠️
</div>
