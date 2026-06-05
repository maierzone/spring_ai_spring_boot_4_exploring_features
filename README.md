# Spring AI Demo – Top 10 Features (Spring Boot 4 / Java 21)

Demonstrationsanwendung für den Einsatz von **Spring AI**. Jedes der zehn
wichtigsten Spring-AI-Features ist als eigener, ausführlich kommentierter
REST-Endpunkt umgesetzt. Als LLM-Provider ist **Anthropic (Claude)** konfiguriert.

| Stack | Version |
|-------|---------|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring AI | 2.0.0-M8 (erste Boot-4-Linie, auf Maven Central) |
| Datenbank | PostgreSQL 17 (via Docker Compose, auto-gestartet) |
| Build | Maven |

## Die Top-10-Features

| # | Feature | Endpunkt | Klasse |
|---|---------|----------|--------|
| 1 | **ChatClient** – Kern-API für Chat | `GET /api/chat?message=…` | `feature01_chatclient` |
| 2 | **Streaming** – Antwort als Token-Strom (SSE) | `GET /api/stream?message=…` | `feature02_streaming` |
| 3 | **Prompt Templates** – Prompts mit Platzhaltern | `GET /api/joke?topic=…&language=…` | `feature03_prompttemplate` |
| 4 | **Structured Output** – Antwort als Java-Record | `GET /api/recipe?dish=…` · `POST /api/tickets/analyze` (Klassifikation, **wird in PostgreSQL persistiert**) · `GET /api/tickets/stats` (Analytics) | `feature04_structured` |
| 5 | **Chat Memory** – mehrstufige Konversation (**persistent in PostgreSQL**) | `POST /api/memory/{conversationId}?message=…` | `feature05_memory` |
| 6 | **Tool Calling** – Modell ruft Java-Methoden auf | `GET /api/tools?message=…` (Produktkatalog-Service) | `feature06_tools` |
| 7 | **RAG** – Wissen aus Vektorspeicher | `GET /api/rag?question=…` · `GET /api/rag/sources?question=…` (Quellen) | `feature07_rag` |
| 8 | **Embeddings** – Text→Vektor, Ähnlichkeit | `GET /api/embeddings/similarity?text1=…&text2=…` | `feature08_embeddings` |
| 9 | **Multimodalität** – Text + Bild | `POST /api/multimodal` (multipart: `image`, `message`) | `feature09_multimodal` |
| 10 | **Advisors** – Interzeptoren (eigener Metrics-Advisor, Logging, Guardrail) | `GET /api/advisors?message=…` | `feature10_advisors` |

### Vertiefte Features (siehe Handbuch `docs/HANDBUCH.md`)

| # | Feature | Endpunkt | Klasse |
|---|---------|----------|--------|
| 11 | **MCP** – Model Context Protocol (Server **und** Client) | MCP-SSE-Endpunkt (Server) · `GET /api/mcp/client/tools` · `GET /api/mcp/client/ask?message=…` | `feature11_mcp` |
| 12 | **Observability** – Micrometer-Metriken (Tokens/Latenz) | `GET /api/observability/ask?message=…` · `GET /api/observability/metrics` · `/actuator/metrics` | `feature12_observability` |
| 13 | **Evaluation** – LLM-as-a-Judge gegen Halluzinationen | `GET /api/evaluate/relevancy?question=…&context=…&answer=…` | `feature13_evaluation` |
| 17 | **pgvector** – persistenter VectorStore (PostgreSQL) | `GET /api/pgvector/search?query=…&category=…` · `POST /api/pgvector/documents?text=…&category=…` · `GET /api/pgvector/info` | `feature17_pgvector` |
| 19 | **Moderation** – Content-Safety als Guardrail-Advisor (Claude-as-Classifier) | `GET /api/moderation?message=…` · `GET /api/moderation/check?text=…` | `feature19_moderation` |
| 20 | **Modulare / Advanced RAG** – `RetrievalAugmentationAdvisor` mit Query-Transformation, -Expansion, Retriever & Augmenter als austauschbare Pipeline | `GET /api/modular-rag?question=…` · `GET /api/modular-rag/retrieve?question=…` (Retriever-Transparenz, ohne API-Key) | `feature20_modularrag` |

Ausführliche Erläuterung dieser drei Themenblöcke (Konzept, Code, Konfiguration,
Fallstricke) im Handbuch: **[`docs/HANDBUCH.md`](docs/HANDBUCH.md)**.

> **Hinweis zu Feature 19 (Moderation):** Anthropic bietet – anders als OpenAI/Mistral –
> keinen dedizierten Moderation-Endpunkt, für den Spring AI ein `ModerationModel`
> autokonfigurieren könnte. Der von Anthropic empfohlene Weg ist, **Claude selbst als
> Klassifikator** über die normale Chat-API zu nutzen. Dieses Feature setzt das als
> selbst geschriebenen `CallAdvisor` um, der Eingabe **und** Antwort prüft (Input-/
> Output-Guardrail) – ohne zweiten Provider und mit demselben Anthropic-API-Key.
> Getestet wird gegen die `ContentModerator`-Abstraktion, das Quality-Gate bleibt offline.

> **Hinweis zu Feature 20 (Modulare RAG):** Während Feature 7 die einfachste
> RAG-Form (`QuestionAnswerAdvisor`: Frage → Suche → Antwort) zeigt, zerlegt der
> `RetrievalAugmentationAdvisor` denselben Ablauf in eine **austauschbare Pipeline**
> aus eigenständigen Komponenten: Query-Transformation (`RewriteQueryTransformer`
> schärft die Suchanfrage), Query-Expansion (`MultiQueryExpander` erzeugt
> Formulierungsvarianten), Retrieval (`VectorStoreDocumentRetriever`) und
> Augmentation (`ContextualQueryAugmenter` mit `allowEmptyContext`). Genutzt wird
> derselbe Vektorspeicher wie in Feature 7. Trade-off: Transformation und Expansion
> sind je ein zusätzlicher kleiner LLM-Aufruf vor der eigentlichen Antwort. Der
> deterministische Retrieval-Schritt wird über `/api/modular-rag/retrieve` ohne
> API-Key sichtbar gemacht und ist offline getestet.

### Agentic Patterns (siehe Handbuch `docs/HANDBUCH-EVALUATOR-OPTIMIZER.md`)

| # | Feature | Endpunkt | Klasse |
|---|---------|----------|--------|
| 16 | **Evaluator-Optimizer** – KI verbessert ihre eigene Ausgabe in einer Kritik-Schleife | `GET /api/evaluator/sql?question=…` | `feature16_evaluator` |

Erzeugtes SQL wird von einem zweiten „Richter"-Modell gegen das Schema bewertet;
bei Mängeln fließt die Begründung als Feedback in einen neuen Versuch – bis es
akzeptiert ist oder die Iterationsgrenze greift. Geschichtete Bewertung
(deterministischer `SqlGuard` vor LLM-Richter) und Konvergenz-Logik im Detail im
Handbuch: **[`docs/HANDBUCH-EVALUATOR-OPTIMIZER.md`](docs/HANDBUCH-EVALUATOR-OPTIMIZER.md)**.

### Datenbank-Features (PostgreSQL via Docker)

Die zuvor rein In-Memory umgesetzten Bereiche sind jetzt an eine echte
**PostgreSQL**-Datenbank angebunden – ohne manuelles Setup:

- **Docker-Compose-Auto-Start:** Dank `spring-boot-docker-compose` erkennt Spring
  Boot 4 die `compose.yaml` im Projektwurzelverzeichnis, startet beim
  `mvn spring-boot:run` selbstständig einen Postgres-Container und verdrahtet die
  `DataSource` automatisch (beim Beenden wird der Container gestoppt). Einzige
  Voraussetzung: eine laufende Docker-Engine.
- **Persistentes Chat-Memory (Feature 5/B):** Das `ChatMemory` legt den
  Gesprächsverlauf nun über ein autokonfiguriertes `JdbcChatMemoryRepository` in
  Postgres ab – der Verlauf **überlebt einen Neustart**. Der übrige Code blieb
  unverändert; getauscht wurde nur das `ChatMemoryRepository`.
- **Ticket-Persistenz + Analytics (Feature 4/E):** Jede `POST /api/tickets/analyze`
  schreibt die typisierte Analyse in eine `tickets`-Tabelle. `GET /api/tickets/stats`
  wertet sie per SQL `GROUP BY` aus – aus KI-Extraktion werden abfragbare
  Geschäftsdaten.
- **Persistenter VectorStore via pgvector (Feature 17):** In der Standardlaufzeit
  (Profil `pgvector`) tritt der In-Memory-`SimpleVectorStore` der RAG-Demo zugunsten
  eines `PgVectorStore` zurück – Embeddings liegen damit in PostgreSQL und
  **überleben einen Neustart**, inkl. DB-seitigem Metadaten-Filter. Dafür nutzt
  `compose.yaml` das Image `pgvector/pgvector:pg17` (statt `postgres:17`). Wieder
  wird nur die Bean getauscht, der RAG-Code bleibt unverändert. Die Tests bleiben
  über `@ActiveProfiles("test")` offline auf dem `SimpleVectorStore` (H2).

Die Tests benötigen **kein Docker**: Sie laufen gegen eine eingebettete
**H2**-Datenbank (Spring AI M8 bringt einen H2-Dialekt für das Chat-Memory-Schema
mit), das Quality-Gate bleibt damit vollständig offline.

### Developer-Fokus (vertiefte Features)

Die für Backend-Entwickler wertvollsten Features sind praxisnah ausgebaut:

- **Structured Output** zusätzlich als *Klassifikation/Extraktion*: ein frei
  formuliertes Support-Ticket wird in einen typisierten Record mit Enums
  (`Category`, `Priority`, `Sentiment`) überführt – direkt routing-/persistierbar.
- **Tool Calling** nach dem realen Muster: die Tools sind dünne Fassaden vor einem
  per DI injizierten Fach-Service (`ProductCatalogService`), nicht Wegwerf-Methoden.
- **RAG** lädt sein Wissen aus einer Ressourcen-Datei (`knowledge/spring-ai-faq.md`,
  ein Absatz = ein Dokument) und bietet einen Transparenz-Endpunkt
  (`/api/rag/sources`), der die abgerufenen Quellen samt Score zeigt – ohne LLM/Key.
- **Advisors**: ein selbst geschriebener `MetricsLoggingAdvisor` (implementiert
  `CallAdvisor`) misst Latenz + Token-Verbrauch und legt sie in den Antwort-Kontext
  – das demonstriert das Erweiterungskonzept, auf dem auch Memory und RAG basieren.

## Beispiel-Requests (curl)

Server vorausgesetzt unter `http://localhost:8080`. Endpunkte ohne LLM-Aufruf
(`/api/embeddings*`, `/api/rag/sources`) funktionieren auch ohne API-Key.

```bash
# 1) ChatClient
curl "localhost:8080/api/chat?message=Erklaere+Spring+AI+in+einem+Satz"

# 2) Streaming (Server-Sent-Events)
curl -N "localhost:8080/api/stream?message=Zaehle+von+1+bis+5"

# 3) Prompt Template
curl "localhost:8080/api/joke?topic=Katzen&language=Englisch"

# 4) Structured Output – Rezept als JSON-Record
curl "localhost:8080/api/recipe?dish=Pfannkuchen"

# 4b) Structured Output – Ticket-Klassifikation (Freitext -> typisiertes JSON)
curl -X POST "localhost:8080/api/tickets/analyze" \
     -H "Content-Type: text/plain" \
     -d "Nach dem letzten Update kann ich mich nicht mehr einloggen, sehr aergerlich!"

# 4c) Ticket-Analytics – Auswertung der persistierten Tickets (SQL GROUP BY)
curl "localhost:8080/api/tickets/stats"

# 5) Chat Memory – mehrstufiger Dialog (gleiche conversationId), in Postgres persistent
curl -X POST "localhost:8080/api/memory/anna?message=Mein+Name+ist+Anna."
curl -X POST "localhost:8080/api/memory/anna?message=Wie+heisse+ich?"

# 6) Tool Calling – Modell ruft den Produktkatalog-Service auf
curl "localhost:8080/api/tools?message=Wie+viele+Monitore+sind+auf+Lager?"

# 7) RAG – Antwort auf Basis des Wissensspeichers
curl "localhost:8080/api/rag?question=Was+ist+RAG?"

# 7b) RAG-Transparenz – welche Quellen liefert die Suche? (ohne API-Key)
curl "localhost:8080/api/rag/sources?question=Was+ist+Tool+Calling?&topK=2"

# 8) Embeddings – Kosinus-Aehnlichkeit zweier Texte (ohne API-Key)
curl "localhost:8080/api/embeddings/similarity?text1=Hund&text2=Katze"

# 9) Multimodalitaet – Bild + Frage (vision-faehiges Claude-Modell noetig)
curl -X POST "localhost:8080/api/multimodal" \
     -F "image=@bild.png" \
     -F "message=Was ist auf diesem Bild zu sehen?"

# 10) Advisors – Guardrail + eigener Metrics-Advisor (siehe Server-Log fuer Latenz/Tokens)
curl "localhost:8080/api/advisors?message=Erklaere+kurz,+was+ein+Advisor+ist."

# 11) MCP-Client – welche Tools bietet ein angebundener MCP-Server? (ohne API-Key;
#     ohne konfigurierten Server kommt ein Hinweis – siehe docs/HANDBUCH.md)
curl "localhost:8080/api/mcp/client/tools"

# 12) Observability – bereits erfasste gen_ai-Metriken (vor 1. Aufruf leer, ohne API-Key)
curl "localhost:8080/api/observability/metrics"
curl "localhost:8080/actuator/metrics/gen_ai.client.token.usage"   # nach einem echten Aufruf

# 13) Evaluation – ist die Antwort durch den Kontext gedeckt? (LLM-as-a-Judge)
curl -G "localhost:8080/api/evaluate/relevancy" \
     --data-urlencode "question=Was ist RAG?" \
     --data-urlencode "context=RAG kombiniert Retrieval mit Generation." \
     --data-urlencode "answer=RAG ruft passende Dokumente ab und nutzt sie als Kontext."

# 19) Moderation – Content-Safety-Guardrail (Claude-as-Classifier, Anthropic-Key)
curl "localhost:8080/api/moderation?message=Erklaere+kurz,+was+Content-Moderation+ist."
curl -G "localhost:8080/api/moderation/check" --data-urlencode "text=Pruefe diesen Text bitte."

# 20) Modulare RAG – volle Pipeline (Rewrite+Expand -> Retrieve -> Augment), Anthropic-Key
curl -G "localhost:8080/api/modular-rag" \
     --data-urlencode "question=Kannst du mir kurz sagen, was dieser GESPERRT-Status bei der eGK bedeutet?"
# 20b) Modulare RAG – nur der Retrieval-Schritt (deterministisch, ohne API-Key)
curl -G "localhost:8080/api/modular-rag/retrieve" --data-urlencode "question=Wozu dient der Heilberufsausweis?"

# 17) pgvector – Dokument persistieren und mit Metadaten-Filter suchen (ohne API-Key)
curl "localhost:8080/api/pgvector/info"          # aktive VectorStore-Implementierung
curl -X POST "localhost:8080/api/pgvector/documents?text=Spring+AI+vereinfacht+RAG&category=spring"
curl -G "localhost:8080/api/pgvector/search" \
     --data-urlencode "query=Wie greife ich auf ein LLM zu?" \
     --data-urlencode "category=spring"
```

## Starten

```bash
export ANTHROPIC_API_KEY=sk-ant-…        # echter Key für reale Modellaufrufe
mvn spring-boot:run                      # startet automatisch den Postgres-Container
```

> **Voraussetzung:** Eine laufende **Docker-Engine** – Spring Boot startet den in
> `compose.yaml` definierten PostgreSQL-Container selbst. Ohne Docker schlägt der
> App-Start fehl (die Tests benötigen Docker hingegen nicht, siehe unten).

Ohne Key starten die Endpunkte zwar, ein echter Modellaufruf schlägt aber mit
HTTP 401 fehl. Die Features 7 und 8 (RAG/Embeddings) funktionieren dank des
eingebauten Offline-Embeddings teilweise auch ohne Key.

## Web-UI / Developer-Konsole

Nach dem Start ist unter **`http://localhost:8080`** eine schlanke, statische
Web-Konsole (`src/main/resources/static`) erreichbar. Sie triggert die
Backend-Endpunkte der Developer-Features direkt aus dem Browser – kein `curl`
nötig:

- **Structured Output (Feature 4):** Support-Ticket eingeben → das typisierte
  Ergebnis wird **grafisch** ausgewertet: Kategorie als farbiges Badge, Priorität
  als Segment-Leiste, Sentiment als SVG-Gauge mit Nadel, plus Zusammenfassung.
- **Tool Calling (Feature 6):** Frage an den Produktkatalog stellen.
- **RAG (Feature 7):** Antwort generieren **oder** „Nur Quellen zeigen" – Letzteres
  funktioniert ohne API-Key und zeigt die abgerufenen Dokumente samt Score.

Die Visualisierung kommt ganz ohne externe Chart-Bibliothek aus (reines SVG/CSS)
und ist damit voll offline.

## Tests / Quality-Gate

```bash
mvn verify
```

Die Testsuite läuft **vollständig offline** und ohne API-Key:

- Reine Logik-Tests (Embeddings, Tools, Vektorsuche).
- Ein Spring-Kontext-Smoke-Test, der bestätigt, dass alle zehn Controller und
  die Spring-AI-Autokonfiguration sauber verdrahten.

Derselbe Befehl läuft im GitHub-Actions-Workflow (`.github/workflows/ci.yml`)
als Quality-Gate bei jedem Push/PR.

### Optional: echter pgvector-Pfad (Docker)

Der `PgVectorStore` aus Feature 17 lässt sich nicht auf H2 prüfen. Dafür gibt es
einen separaten Integrationstest (`PgVectorPostgresIntegrationTest`, mit
`@Tag("docker")`), der per **Testcontainers** eine echte pgvector-PostgreSQL
hochfährt und Ablegen/Suche inkl. Metadaten-Filter verifiziert. Er ist aus dem
Standardlauf ausgeschlossen und braucht eine Docker-Engine – ein API-Key ist
**nicht** nötig:

```bash
mvn test -Dtest.excludedGroups= -Dgroups=docker
```

In CI fährt ihn der separate, **manuell** ausgelöste Workflow
`.github/workflows/ci-docker.yml` (`workflow_dispatch`).

## Offline-Embedding (bewusste Designentscheidung)

Der Anthropic-Provider liefert kein Embedding-Modell. Statt einen zweiten
Cloud-Provider (API-Key) oder einen HuggingFace-Download in die CI zu zwingen,
enthält die Demo ein kleines, deterministisches `HashingEmbeddingModel`. Es
implementiert das Standard-`EmbeddingModel`-Interface – für Produktion ließe es
sich 1:1 gegen ein echtes Embedding-Modell tauschen, ohne den übrigen Code zu
ändern.
