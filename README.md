# Spring AI Demo – Top 10 Features (Spring Boot 4 / Java 21)

Demonstrationsanwendung für den Einsatz von **Spring AI**. Jedes der zehn
wichtigsten Spring-AI-Features ist als eigener, ausführlich kommentierter
REST-Endpunkt umgesetzt. Als LLM-Provider ist **Anthropic (Claude)** konfiguriert.

| Stack | Version |
|-------|---------|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring AI | 2.0.0-M8 (erste Boot-4-Linie, auf Maven Central) |
| Build | Maven |

## Die Top-10-Features

| # | Feature | Endpunkt | Klasse |
|---|---------|----------|--------|
| 1 | **ChatClient** – Kern-API für Chat | `GET /api/chat?message=…` | `feature01_chatclient` |
| 2 | **Streaming** – Antwort als Token-Strom (SSE) | `GET /api/stream?message=…` | `feature02_streaming` |
| 3 | **Prompt Templates** – Prompts mit Platzhaltern | `GET /api/joke?topic=…&language=…` | `feature03_prompttemplate` |
| 4 | **Structured Output** – Antwort als Java-Record | `GET /api/recipe?dish=…` | `feature04_structured` |
| 5 | **Chat Memory** – mehrstufige Konversation | `POST /api/memory/{conversationId}?message=…` | `feature05_memory` |
| 6 | **Tool Calling** – Modell ruft Java-Methoden auf | `GET /api/tools?message=…` | `feature06_tools` |
| 7 | **RAG** – Wissen aus Vektorspeicher | `GET /api/rag?question=…` | `feature07_rag` |
| 8 | **Embeddings** – Text→Vektor, Ähnlichkeit | `GET /api/embeddings/similarity?text1=…&text2=…` | `feature08_embeddings` |
| 9 | **Multimodalität** – Text + Bild | `POST /api/multimodal` (multipart: `image`, `message`) | `feature09_multimodal` |
| 10 | **Advisors** – Interzeptoren (Logging, Guardrail) | `GET /api/advisors?message=…` | `feature10_advisors` |

## Starten

```bash
export ANTHROPIC_API_KEY=sk-ant-…        # echter Key für reale Modellaufrufe
mvn spring-boot:run
```

Ohne Key starten die Endpunkte zwar, ein echter Modellaufruf schlägt aber mit
HTTP 401 fehl. Die Features 7 und 8 (RAG/Embeddings) funktionieren dank des
eingebauten Offline-Embeddings teilweise auch ohne Key.

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

## Offline-Embedding (bewusste Designentscheidung)

Der Anthropic-Provider liefert kein Embedding-Modell. Statt einen zweiten
Cloud-Provider (API-Key) oder einen HuggingFace-Download in die CI zu zwingen,
enthält die Demo ein kleines, deterministisches `HashingEmbeddingModel`. Es
implementiert das Standard-`EmbeddingModel`-Interface – für Produktion ließe es
sich 1:1 gegen ein echtes Embedding-Modell tauschen, ohne den übrigen Code zu
ändern.
