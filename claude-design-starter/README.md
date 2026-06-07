# Spring AI Console — Design System

A design system for the **Spring AI Developer Console** — the browser-based control
surface for a Spring AI demonstration backend (Spring Boot 4 · Java 21 · Anthropic
Claude). It gives design agents the tokens, components, and brand rules to build new
screens, prototypes, slides, and assets that feel native to this product.

> **Visual direction note.** The original repository ships a green "Light Terminal"
> aesthetic. This design system **intentionally departs** from it, per the product
> owner's brief: a clean, **Material Design 3-equivalent** language in **black,
> white, and marine blue**, with soft elevation, generous padding, and a calm
> developer-tooling atmosphere. The *product context* (what the app does, how copy
> reads) is lifted faithfully from the source; the *visual skin* is new.

---

## What is this product?

The Spring AI Console is the front end for **"Spring AI Demo – Top 10 Features"**, a
teaching/reference backend that demonstrates the most important Spring AI capabilities,
each as a documented REST endpoint. The console lets a developer trigger those
endpoints from the browser instead of `curl`, and visualises the results.

Core feature surfaces exposed in the console:

| Feature | What it does | Endpoint |
|---|---|---|
| **Gate-Decider** | An AI router — one input for everything; the model picks the responsible feature and delegates. | `GET /api/gateway` |
| **Structured Output** | Free-text support ticket → typed analysis (Category / Priority / Sentiment), rendered as badge + segment bar + sentiment gauge. | `POST /api/tickets/analyze` |
| **Tool Calling** | The model calls Java methods (product-catalog service) for live data. | `GET /api/tools` |
| **RAG** | Answers grounded in a vector store; a "sources" mode shows retrieved documents + scores (no API key needed). | `GET /api/rag`, `/api/rag/sources` |
| **DB Query** | Natural-language aggregation questions against the database (guarded text-to-SQL). | `GET /api/db/ask` |

Behind these sit the full "Top 10+" feature set: ChatClient, Streaming (SSE), Prompt
Templates, Chat Memory (persistent in PostgreSQL), Embeddings, Multimodality, Advisors,
MCP, Observability, Evaluation, pgvector, and an Evaluator-Optimizer agentic loop.

**Stack:** Java 21 · Spring Boot 4.0.6 · Spring AI 2.0.0-M8 · PostgreSQL 17 (pgvector) ·
Maven · Anthropic Claude as the LLM provider.

---

## Sources

This system was derived from a single GitHub repository (read the source to do a
better job building against this product):

- **maierzone/spring_ai_spring_boot_4_exploring_features**
  https://github.com/maierzone/spring_ai_spring_boot_4_exploring_features
  - `src/main/resources/static/` — the original web console (`index.html`, `styles.css`, `app.js`)
  - `slides/slides.md` — a Slidev deck ("Spring AI – Backend Working Group")
  - `README.md`, `docs/HANDBUCH*.md` — feature documentation
  - `knowledge/spring-ai-faq.md` — RAG knowledge base copy

The reader is encouraged to explore that repository directly for endpoint behaviour,
record/enum shapes, and additional features not surfaced in the console.

---

## CONTENT FUNDAMENTALS

**Language.** Primary UI language is **German**. Copy is written for **professional
backend developers**, not end consumers. Technical English terms are kept untranslated
where they are the idiom (*ChatClient, Tool Calling, Structured Output, RAG, VectorStore,
Embeddings, Advisor, Streaming*). Slide decks may mix in more English headings.

**Voice & address.** Neutral-professional and direct. Instructions use the implied
imperative ("Frage eingeben", "Antwort generieren") rather than addressing the user as
*du*/*Sie*. The product talks about *what the model does* in plain declaratives:
*"Das Modell ruft bei Bedarf den Katalog-Service auf."* No marketing fluff, no hype.

**Tone.** Pragmatic, precise, slightly didactic — it is a teaching tool. Confidence
without salesmanship. Favours the concrete ("Freitext rein → typisierte Analyse") over
the abstract. German nouns are capitalised per orthography; otherwise sentence case.

**Casing.** Sentence case for headings and body. Endpoint/file references are mono and
verbatim (`POST /api/tickets/analyze`, `feature04_structured`). Button labels in the
original are terse, lowercase, command-like (*route, analyze, run, ask, sources*) —
a deliberate CLI echo. This system keeps that convention for primary actions.

**Emoji.** **Not used in the application UI.** The Slidev deck uses topical emoji as
list bullets (🧩 ⚙️ 🧠 🍃 📦 🔗 🧪). For console/product surfaces, prefer
**Material Symbols** icons instead of emoji. The leaf 🍃 is Spring's informal motif and
may appear in slide/marketing contexts only.

**Vibe.** "Developer console you actually enjoy opening." Calm, legible, fast. Every
panel maps to a real endpoint; nothing is decorative filler. Code and commands are
first-class citizens, shown in monospace.

**Specific copy examples (from source):**
- Eyebrow / file tag: `04_structured_output — ticket-analyze.sh`
- Card title: *"Structured Output · Ticket-Analyse"*
- Hint: *"Freitext rein → typisierte Analyse (Kategorie, Priorität, Sentiment). Ruft `POST /api/tickets/analyze` auf. (Benötigt einen gültigen API-Key.)"*
- Prompt banner: `spring-ai@demo:~/features$ ./dev-console`
- Empty/missing-key hint: *"(Ist ANTHROPIC_API_KEY gesetzt?)"*

---

## VISUAL FOUNDATIONS

The system is a **Material Design 3-equivalent** language. It borrows M3's structure —
tonal palette, surface roles, shape scale, elevation levels, state layers, type scale —
but commits to a restrained **marine-blue + black + white** identity rather than M3's
default purple.

**Color.** One accent: **marine blue** (`--marine-40 #1B4D89`) for primary actions,
active states, focus rings, and links. Black-leaning **ink** (`--neutral-10 #11161D`)
for text. White and cool off-white **surfaces** (`#FFFFFF`, `#F6F8FB`, `#F4F6FA`
canvas). Greys are subtly cool (blue-tinted), never warm. Standard semantic colors —
error red, success green, amber warning, cyan info — appear only on status, badges,
and data viz. The palette is built as full M3 tonal ramps so containers/on-colors stay
harmonious. **Marine is used sparingly** — it should feel like a precise highlight, not
a flood.

**Type.** UI/display in **Hanken Grotesk** (a clean geometric grotesque — chosen as an
M3-Roboto-equivalent that isn't Inter/Roboto). Code, endpoints, file tags, and CLI-style
labels in **JetBrains Mono** (the deck's mono family). A full M3 type scale is provided
(Display / Headline / Title / Body / Label) plus mono Code roles. Display sizes carry
slight negative tracking; mono "eyebrow" labels are uppercase with positive tracking.

**Spacing.** 4px base grid (`4 8 12 16 24 32 48 64`). Cards use generous internal
padding (20–28px). Layouts breathe — the "clean working space" feel comes from
whitespace, not dividers.

**Backgrounds.** Flat cool off-white canvas (`#F4F6FA`). **No gradients** in product UI
(the one exception: slide cover backgrounds may use a deep marine gradient). No photographic
imagery, no hand-drawn illustration, no repeating texture. The original's faint grid is
dropped in favour of clean flat surfaces. The single intentional dark surface is the
**console output** block (`#0C1622`) — real terminal output rendered in mono.

**Elevation & shadows.** M3 six-level elevation (0–5) with **soft, marine-tinted**
shadows (low-opacity `rgba(13,27,42,…)`), never harsh black drop shadows. Cards rest at
elevation 1; hover lifts to elevation 2–3; menus/dialogs sit at 3–5. Shadows are diffuse
and short — "resting on paper", not floating.

**Corners.** Shape scale `4 / 8 / 12 / 16 / 28` + full. Buttons & chips are pill/`full`
or `8px`; text fields `8–12px`; cards `12–16px`; dialogs/sheets `28px`. Consistent, never
mixed within a component.

**Borders.** Hairline `1px` outlines (`--outline #C2CBD6`) on outlined buttons, text
fields, and outlined cards; `--outline-variant` for subtle dividers. Filled surfaces
generally rely on elevation, not borders.

**Cards.** White surface, `12–16px` radius, elevation 1, ~24px padding. Optional
hairline outline for the "outlined card" variant (elevation 0). On hover, interactive
cards lift one elevation step and the marine state layer brushes in. No colored
left-border accents.

**Animation.** M3 easing (`cubic-bezier(.2,0,0,1)`), short (150ms) for state changes,
medium (250ms) for entrances, long (400ms) for emphasis. Result panels **fade-and-rise**
(`translateY` + opacity). Badges **pop-in** (scale). The sentiment gauge **needle
sweeps** to position. Buttons lift on hover, settle on press. No bounces, no infinite
decorative loops. Respect `prefers-reduced-motion`.

**Interaction states (M3 state layers).** Hover = an 8% overlay of the element's "on"
color; focus = 10% + a 3px marine focus ring; press = 10% + a subtle scale-down. Text
fields focus to a marine border + soft marine glow ring. Disabled = ~38% opacity.

**Transparency & blur.** Used sparingly — scrim behind dialogs (black ~32%); optional
frosted top app bar on scroll. Not a primary motif.

**Imagery vibe.** N/A — this is a tooling product with no photography. Where a visual is
needed (e.g. slide cover), use a deep, **cool marine** field, not warm tones.

---

## Iconography

See the **Iconography** section below — short version: use **Material Symbols** (Google's
M3 icon set), Rounded style, weight 400, optical size 24, loaded from CDN. See
`assets/README.md` for usage and the substitution flag.

---

## File index (manifest)

Root:
- **`README.md`** — this file (product context, content fundamentals, visual foundations, iconography).
- **`colors_and_type.css`** — all design tokens: marine + neutral palettes, semantic roles, type scale, shape, spacing, elevation, motion.
- **`SKILL.md`** — Agent-Skill front-matter so this folder works as a downloadable Claude skill.
- **`preview/`** — small HTML specimen cards that populate the Design System tab (type, color, spacing, components).
- **`assets/`** — brand wordmark, icon usage docs (Material Symbols), and any copied visual assets.
- **`ui_kits/console/`** — the Spring AI Developer Console UI kit: `index.html` (interactive recreation) + JSX components.
- **`slides/`** — sample presentation slides in the marine M3 brand (`index.html` + slide JSX), adapted from the repo's Slidev deck.

Start with `colors_and_type.css` for tokens, then open `ui_kits/console/index.html` to
see the system assembled into a real product view.
