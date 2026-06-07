# Sample Slides — Spring AI (marine M3 brand)

A small set of presentation slides in this design system's marine / Material-Design-3
language, adapted from the repo's Slidev deck (`slides/slides.md`,
"Spring AI – Backend Working Group").

Open `index.html`. Navigate with ←/→, click thumbnails, or
`document.querySelector('deck-stage').goTo(n)`.

## Slide types demonstrated

| # | Type | Use for |
|---|---|---|
| 1 | **Cover** | Title slide — deep marine gradient, wordmark, version chips. |
| 2 | **Two-column + code** | A concept with bullets on the left and a dark code card on the right. |
| 3 | **Architecture / flow** | Building-block nodes flowing into a hero node. |
| 4 | **Feature deep-dive** | Same two-col template, framed as a numbered feature with a file/endpoint ref. |
| 5 | **Statement** | Full-bleed marine slide for a single big idea. |
| 6 | **Takeaways** | Two-column summary grid with iconed headings. |

## Build notes

- Built on the shared **`deck-stage.js`** component (1920×1080 canvas, auto-scaled).
- Slides are authored as static `<section>` children so headings stay directly editable.
- Tokens come from `../colors_and_type.css`; icons are **Material Symbols Rounded**.
- Code cards reuse the console dark surface (`--console-bg`) with light syntax accents.
- Cover and statement slides use a deep marine gradient — the **only** place gradients
  appear in this system (product UI stays flat).
- Entrance animations fade-and-rise and are gated on `[data-deck-active]` +
  `prefers-reduced-motion`, so print/PDF and reduced-motion show content immediately.

> Material Symbols ligatures may rasterise as text in static screenshot tools; they
> render as glyphs in a real browser.
