# Spring AI – Foliendeck (Slidev)

Begleitendes Präsentationsdeck zur Backend-Working-Group-Demo. Stellt die
Spring-AI-Kern-Features visuell vor; jede Feature-Folie verweist unten rechts
auf die konkrete Beispiel-Datei und den Endpoint im Projekt — so lässt sich
direkt von der Folie nach IntelliJ wechseln.

## Inhalt (~12 Folien)

1. Titel
2. Was ist Spring AI?
3. Architektur (ChatClient → Advisors → Model / VectorStore)
4. ChatClient
5. Prompt Templates
6. Structured Output
7. Tool Calling
8. RAG
9. Chat Memory
10. Querschnitt: Advisors
11. Live-Demo (Console + curl)
12. Key Takeaways & Repo-Wegweiser

## Starten

```bash
cd slides
npm install
npm run dev      # öffnet http://localhost:3030 mit Live-Reload
```

Im Präsentationsmodus:

- `f` Vollbild · `o` Folienübersicht · `d` Dark/Light · Pfeiltasten zum Blättern
- Presenter-Ansicht: `http://localhost:3030/presenter`

## Export

```bash
npm run build    # statisches HTML nach slides/dist/
npm run export   # PDF (benötigt Playwright-Chromium)
```

Quelle: [`slides.md`](./slides.md) · Theme: `@slidev/theme-seriph`
