# UI Kit — Spring AI Developer Console

A high-fidelity, interactive recreation of the **Spring AI Developer Console** in the
marine / Material-Design-3 brand of this design system. It is the browser control
surface that triggers the demo backend's feature endpoints and visualises results.

> **Recreation, not the original.** The source repo ships a green "Light Terminal" UI
> (`src/main/resources/static/`). This kit keeps the *product structure, copy, endpoints,
> and result visualisations* faithfully, but re-skins them into the M3-marine language.
> Responses are **faked client-side** — no backend, no API key.

## Run it

Open `index.html`. Pick a feature in the left navigation drawer; each panel has a real
input and a command-style action button that returns a canned, on-brand response after a
short "thinking" delay.

## Screens / interactions covered

| Feature | Interaction |
|---|---|
| **Gate-Decider** | `route` a question → shows the chosen route + delegated answer in the console. |
| **Structured Output** | `analyze` a support ticket → category badge, animated priority segment bar, sentiment gauge, summary. |
| **Tool Calling** | `run` a catalog question → console shows the tool call + result. |
| **RAG** | `ask` for an answer **or** `sources` → ranked document cards with similarity scores. |
| **DB-Abfrage** | `ask` a natural-language aggregation → guarded SQL + result. |

## Files

- **`index.html`** — entry point; loads tokens, kit CSS, and the Babel-compiled JSX.
- **`console.css`** — kit layout & component styles (app bar, nav drawer, panel card, fields, buttons, console, result viz). All colors/type/elevation pull from `../../colors_and_type.css`.
- **`components.jsx`** — primitives + chrome: `Icon`, `Button`, `TopAppBar`, `NavDrawer`, and the `FEATURES` list.
- **`panels.jsx`** — one component per feature (`GatewayPanel`, `StructuredPanel`, `ToolsPanel`, `RagPanel`, `DbPanel`) plus the shared `Panel` wrapper and fake-run helper.
- **`app.jsx`** — composes the app shell and tracks the active feature.

## Component vocabulary

- **Top app bar** — wordmark + stack version chips (mono) + a success "API-Key aktiv" pill.
- **Navigation drawer** — M3 drawer with pill items; active item uses `primary-container` and a filled icon.
- **Feature panel** — elevated card: icon tile + mono eyebrow (the source's `NN_feature — file.sh` tag) + title + hint + field + command button.
- **Buttons** — filled (primary action), tonal, outlined (secondary like `sources`); busy state swaps in a spinner.
- **Fields** — outlined text/textarea with leading Material Symbol; marine focus ring.
- **Console output** — the single dark surface; mono, marine left-rule, route/ok/dim accents; fade-and-rise entrance.
- **Result viz** — category badge (pop-in), priority segments (grow-x), sentiment gauge (sweeping dot), source cards with score chips.

## Notes & fidelity

- Icons are **Material Symbols Rounded** (loaded via the token CSS `@import`). In static
  screenshot tools the ligatures may rasterise as text; in a real browser they render as glyphs.
- Endpoint paths, feature names, and German copy are lifted verbatim from the source
  console and slide deck. The eGK/DB-query feature reflects the repo's `feature14`/`schema-egk.sql` context.
