# Assets & Iconography

## Iconography

The Spring AI Console uses **Material Symbols** — Google's official Material Design 3
icon set — as its icon system. This is the canonical match for an M3-equivalent
language.

- **Family:** Material Symbols **Rounded**
- **Weight:** 400 · **Fill:** 0 (outlined) by default; Fill 1 for active/selected states
- **Optical size:** 24 (UI), 20 for dense rows, 40 for empty states
- **Color:** inherits `currentColor` — `--on-surface-variant` at rest, `--primary` when active
- **Stroke vibe:** rounded terminals, even weight — pairs with Hanken Grotesk's geometry

**Load from CDN:**
```html
<link rel="stylesheet"
  href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200" />
```
```html
<span class="material-symbols-rounded">bolt</span>
```
```css
.material-symbols-rounded {
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
}
```

**Common glyphs in this product:**
`hub` (gate/router), `account_tree` (structured output), `build` / `construction`
(tool calling), `manage_search` (RAG), `database` (DB query), `bolt` (run/stream),
`terminal` (console), `memory` (chat memory), `psychology` (model/AI), `done` /
`error` / `warning` (status), `content_copy`, `chevron_right`, `tune`.

> **Substitution flag.** The source repository has **no icon assets** of its own — the
> original UI uses Unicode glyphs (the `❯` prompt caret) and CSS traffic-light dots, and
> the slide deck uses emoji bullets. Material Symbols is introduced here as the closest
> M3-canonical icon system. If the product later standardises on a different set
> (e.g. Lucide, Phosphor), swap the CDN link and re-document — the stroke weight to match
> is ~1.5–2px even-weight rounded.

**Emoji & Unicode.** Emoji are **not** used in product UI. The `❯` (U+276F) caret and
`$` prompt prefix from the original CLI aesthetic may appear inside the dark console
output block as flavour, in mono.

## Brand mark

The product has no formal logo in the source — its identity is the CLI banner
`spring-ai@demo:~/features$ ./dev-console`. This system provides a simple **type-based
wordmark** lockup (no custom illustration):

- **`wordmark.html`** — "Spring AI **Console**" lockup: Hanken Grotesk semibold, marine
  accent on "Console", with an optional Material Symbols `terminal` glyph in a marine
  rounded badge. Preview it directly.

Use the wordmark in the top app bar, slide footers, and login screens. Keep clear space
of at least the cap-height around it. Never recolor the accent away from marine.
