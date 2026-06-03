"use strict";

// ---------------------------------------------------------------------------
// Farb-Zuordnungen für die grafische Auswertung (müssen zu den Enums im
// Backend passen: StructuredOutputController.Category/Priority/Sentiment).
// ---------------------------------------------------------------------------
const CATEGORY_COLORS = {
  BUG: "#ef4444",
  FEATURE_REQUEST: "#3b82f6",
  QUESTION: "#a855f7",
  BILLING: "#f59e0b",
  OTHER: "#64748b",
};

const PRIORITY_ORDER = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const PRIORITY_COLORS = {
  LOW: "#22c55e",
  MEDIUM: "#eab308",
  HIGH: "#f97316",
  URGENT: "#ef4444",
};

// Sentiment -> Nadelwinkel (Grad, 0°=rechts/positiv, 180°=links/negativ) + Farbe.
const SENTIMENT = {
  NEGATIVE: { angle: 150, color: "#ef4444" },
  NEUTRAL: { angle: 90, color: "#64748b" },
  POSITIVE: { angle: 30, color: "#22c55e" },
};

// --- Helfer ---------------------------------------------------------------

/** Setzt einen Button während eines laufenden Requests auf "deaktiviert". */
async function withBusy(button, fn) {
  const prev = button.textContent;
  button.disabled = true;
  try {
    return await fn();
  } finally {
    button.disabled = false;
    button.textContent = prev;
  }
}

/** Zeigt eine einfache Text-/Fehler-Ausgabe in einem Ergebnis-Container an. */
function showResult(el, text, isError) {
  el.textContent = text;
  el.classList.remove("hidden");
  el.classList.toggle("error", !!isError);
}

// --- Feature 4: Structured Output -----------------------------------------

async function analyzeTicket() {
  const btn = document.getElementById("analyzeBtn");
  const text = document.getElementById("ticketText").value.trim();
  const viz = document.getElementById("ticketViz");
  const err = document.getElementById("ticketError");
  err.classList.add("hidden");

  await withBusy(btn, async () => {
    try {
      const res = await fetch("/api/tickets/analyze", {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: text,
      });
      if (!res.ok) {
        throw new Error("HTTP " + res.status + " – " + (await res.text()));
      }
      const analysis = await res.json();
      renderAnalysis(analysis);
      viz.classList.remove("hidden");
    } catch (e) {
      viz.classList.add("hidden");
      showResult(err, "Analyse fehlgeschlagen: " + e.message
        + "\n(Ist ANTHROPIC_API_KEY gesetzt?)", true);
    }
  });
}

/** Rendert das typisierte Analyse-Objekt grafisch. */
function renderAnalysis(a) {
  // Kategorie als farbiges Badge.
  const badge = document.getElementById("categoryBadge");
  badge.textContent = a.category;
  badge.style.background = CATEGORY_COLORS[a.category] || "#64748b";

  // Priorität als gefüllte Segment-Leiste.
  const segs = document.getElementById("prioritySegments");
  const activeIndex = PRIORITY_ORDER.indexOf(a.priority);
  const color = PRIORITY_COLORS[a.priority] || "#64748b";
  segs.innerHTML = PRIORITY_ORDER.map((level, i) => {
    const active = i <= activeIndex;
    const bg = active ? `background:${color};` : "";
    return `<div class="seg ${active ? "active" : ""}" style="${bg}">${level}</div>`;
  }).join("");

  // Sentiment als SVG-Gauge mit Nadel.
  renderSentimentGauge(a.customerSentiment);

  document.getElementById("ticketSummary").textContent = a.summary || "–";
}

/** Polar -> kartesisch auf dem Halbkreis (Mittelpunkt 80/80, Radius r). */
function polar(angleDeg, r) {
  const rad = (angleDeg * Math.PI) / 180;
  return { x: 80 + r * Math.cos(rad), y: 80 - r * Math.sin(rad) };
}

/** Pfad für einen Bogen-Abschnitt (als Stroke) von startDeg nach endDeg. */
function arc(startDeg, endDeg, r) {
  const s = polar(startDeg, r);
  const e = polar(endDeg, r);
  return `M ${s.x} ${s.y} A ${r} ${r} 0 0 1 ${e.x} ${e.y}`;
}

function renderSentimentGauge(sentiment) {
  const s = SENTIMENT[sentiment] || SENTIMENT.NEUTRAL;
  const r = 64;
  // Drei farbige Zonen: NEGATIV (links) – NEUTRAL (mitte) – POSITIV (rechts).
  const zones = `
    <path d="${arc(180, 120, r)}" stroke="${SENTIMENT.NEGATIVE.color}" stroke-width="14" fill="none" stroke-linecap="round"/>
    <path d="${arc(120, 60, r)}"  stroke="${SENTIMENT.NEUTRAL.color}"  stroke-width="14" fill="none"/>
    <path d="${arc(60, 0, r)}"    stroke="${SENTIMENT.POSITIVE.color}" stroke-width="14" fill="none" stroke-linecap="round"/>`;
  // Nadel als Gruppe – senkrecht nach oben gezeichnet (= Neutral), wird dann per
  // CSS-Transform in die Zielposition gedreht (animiertes Einschwingen).
  const needle = `<g class="needle">
      <line x1="80" y1="80" x2="80" y2="${80 - (r - 6)}" stroke="${s.color}" stroke-width="3"/>
      <circle cx="80" cy="80" r="5" fill="${s.color}"/>
    </g>`;

  const svg = document.getElementById("sentimentGauge");
  svg.innerHTML = zones + needle;

  // Positiver Drehwinkel = im Uhrzeigersinn. Oben (=Neutral) entspricht 90°.
  const rotation = 90 - s.angle;
  const group = svg.querySelector(".needle");
  group.style.transform = "rotate(0deg)";
  // Im nächsten Frame auf den Zielwinkel setzen, damit die CSS-Transition greift.
  requestAnimationFrame(() => {
    group.style.transform = `rotate(${rotation}deg)`;
  });

  const label = document.getElementById("sentimentLabel");
  label.textContent = sentiment;
  label.style.color = s.color;
}

// --- Feature 6: Tool Calling ----------------------------------------------

async function askTools() {
  const btn = event.currentTarget;
  const msg = document.getElementById("toolsInput").value.trim();
  const out = document.getElementById("toolsResult");
  await withBusy(btn, async () => {
    try {
      const res = await fetch("/api/tools?message=" + encodeURIComponent(msg));
      const body = await res.text();
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + body);
      showResult(out, body, false);
    } catch (e) {
      showResult(out, e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)", true);
    }
  });
}

// --- Feature 7: RAG --------------------------------------------------------

async function askRag() {
  const btn = event.currentTarget;
  const q = document.getElementById("ragInput").value.trim();
  const out = document.getElementById("ragResult");
  await withBusy(btn, async () => {
    try {
      const res = await fetch("/api/rag?question=" + encodeURIComponent(q));
      const body = await res.text();
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + body);
      showResult(out, body, false);
    } catch (e) {
      showResult(out, e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)", true);
    }
  });
}

// --- Feature 14: Frag deine Datenbank -------------------------------------

async function askDb() {
  const btn = event.currentTarget;
  const q = document.getElementById("dbInput").value.trim();
  const out = document.getElementById("dbResult");
  await withBusy(btn, async () => {
    try {
      const res = await fetch("/api/db/ask?question=" + encodeURIComponent(q));
      const body = await res.text();
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + body);
      showResult(out, body, false);
    } catch (e) {
      showResult(out, e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt und die DB befüllt?)", true);
    }
  });
}

/** Zeigt die abgerufenen Quellen – funktioniert ohne API-Key. */
async function showSources() {
  const btn = event.currentTarget;
  const q = document.getElementById("ragInput").value.trim();
  const out = document.getElementById("ragResult");
  await withBusy(btn, async () => {
    try {
      const res = await fetch("/api/rag/sources?question=" + encodeURIComponent(q) + "&topK=3");
      const sources = await res.json();
      if (!res.ok) throw new Error("HTTP " + res.status);
      const text = sources
        .map((s, i) => `#${i + 1}  [score ${Number(s.score).toFixed(3)}]  (${s.source})\n${s.text}`)
        .join("\n\n");
      showResult(out, text || "Keine Treffer.", false);
    } catch (e) {
      showResult(out, e.message, true);
    }
  });
}
