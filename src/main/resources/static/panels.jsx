// Spring AI Developer Console — feature panels with real API calls.
const { useState } = React;

function Panel({ icon, eyebrow, title, hint, children }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <div className="p-ic"><Icon name={icon} /></div>
        <div>
          <div className="eyebrow">{eyebrow}</div>
          <h2>{title}</h2>
        </div>
      </div>
      <div className="panel-body">
        <p className="hint">{hint}</p>
        {children}
      </div>
    </section>
  );
}

function useFetch() {
  const [busy, setBusy] = useState(false);
  const run = async (fn) => {
    setBusy(true);
    try { await fn(); } finally { setBusy(false); }
  };
  return [busy, run];
}

function ConsoleOut({ text, error }) {
  if (!text) return null;
  return <div className={"console" + (error ? " error" : "")}>{text}</div>;
}

// --- Gate-Decider ---------------------------------------------------------
function GatewayPanel() {
  const [q, setQ] = useState("Wie viele Versicherte haben E11.9?");
  const [out, setOut] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const go = () => run(async () => {
    setErr(null); setOut(null);
    try {
      const res = await fetch("/api/gateway?question=" + encodeURIComponent(q));
      const body = await res.json();
      if (!res.ok) throw new Error("HTTP " + res.status);
      setOut("→ Route: " + body.route + "\n\n" + body.antwort);
    } catch (e) {
      setErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)");
    }
  });

  return (
    <Panel icon="hub" eyebrow="00_gate_decider — route.sh" title="Gate-Decider · KI-Router"
      hint={<>Ein Eingang für alles: Die KI entscheidet, welches Feature zuständig ist, und delegiert an dessen Endpunkt. Ruft <span className="inline-code">GET /api/gateway</span> auf.</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="forum" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="alt_route" busy={busy} onClick={go}>route</Button></div>
      <ConsoleOut text={out} />
      <ConsoleOut text={err} error />
    </Panel>
  );
}

// --- Structured Output ----------------------------------------------------
const CAT_COLORS = { BUG: "#ef4444", FEATURE_REQUEST: "#3b82f6", QUESTION: "#a855f7", BILLING: "#f59e0b", OTHER: "#64748b" };
const PRIO = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const PRIO_COLORS = { LOW: "#22c55e", MEDIUM: "#eab308", HIGH: "#f97316", URGENT: "#ef4444" };
const SENT_MAP = { NEGATIVE: { pos: "16%", color: "#ef4444" }, NEUTRAL: { pos: "50%", color: "#64748b" }, POSITIVE: { pos: "84%", color: "#1E7D45" } };

function StructuredPanel() {
  const [text, setText] = useState("Nach dem letzten Update kann ich mich nicht mehr einloggen. Das ist extrem ärgerlich, ich brauche dringend eine Lösung!");
  const [a, setA] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const analyze = () => run(async () => {
    setErr(null); setA(null);
    try {
      const res = await fetch("/api/tickets/analyze", {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: text,
      });
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + (await res.text()));
      setA(await res.json());
    } catch (e) {
      setErr("Analyse fehlgeschlagen: " + e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)");
    }
  });

  const sent = a ? (SENT_MAP[a.customerSentiment] || SENT_MAP.NEUTRAL) : null;
  const activeP = a ? PRIO.indexOf(a.priority) : -1;

  return (
    <Panel icon="account_tree" eyebrow="04_structured_output — ticket-analyze.sh" title="Structured Output · Ticket-Analyse"
      hint={<>Freitext rein → typisierte Analyse (Kategorie, Priorität, Sentiment). Ruft <span className="inline-code">POST /api/tickets/analyze</span> auf.</>}>
      <label className="field-label">Support-Ticket</label>
      <div className="field"><textarea value={text} onChange={e => setText(e.target.value)} /></div>
      <div className="row"><Button icon="bolt" busy={busy} onClick={analyze}>analyze</Button></div>
      {a && (
        <div className="viz">
          <div className="viz-card">
            <div className="vc-label">Kategorie</div>
            <span className="cat-badge" style={{ background: CAT_COLORS[a.category] || "#64748b" }}>{a.category}</span>
          </div>
          <div className="viz-card">
            <div className="vc-label">Priorität</div>
            <div className="segments">
              {PRIO.map((p, i) => (
                <div key={p} className={"seg" + (i <= activeP ? " active" : "")}
                  style={i <= activeP ? { background: PRIO_COLORS[a.priority], animationDelay: (i * .07) + "s" } : null}>{p}</div>
              ))}
            </div>
          </div>
          <div className="viz-card full">
            <div className="vc-label">Kunden-Sentiment</div>
            <div className="gauge-wrap">
              <div className="gauge-track"><div className="gauge-dot" style={{ left: sent.pos }}></div></div>
              <span className="gauge-label" style={{ color: sent.color }}>{a.customerSentiment}</span>
            </div>
          </div>
          <div className="viz-card full">
            <div className="vc-label">Zusammenfassung</div>
            <div className="summary">{a.summary}</div>
          </div>
        </div>
      )}
      <ConsoleOut text={err} error />
    </Panel>
  );
}

// --- Tool Calling ---------------------------------------------------------
function ToolsPanel() {
  const [q, setQ] = useState("Wie viele Monitore sind auf Lager?");
  const [out, setOut] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const go = () => run(async () => {
    setErr(null); setOut(null);
    try {
      const res = await fetch("/api/tools?message=" + encodeURIComponent(q));
      const body = await res.text();
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + body);
      setOut(body);
    } catch (e) {
      setErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)");
    }
  });

  return (
    <Panel icon="construction" eyebrow="06_tool_calling — product-catalog.sh" title="Tool Calling · Produktkatalog"
      hint={<>Das Modell ruft bei Bedarf den Katalog-Service auf. Ruft <span className="inline-code">GET /api/tools</span> auf.</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="inventory_2" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="play_arrow" busy={busy} onClick={go}>run</Button></div>
      <ConsoleOut text={out} />
      <ConsoleOut text={err} error />
    </Panel>
  );
}

// --- RAG ------------------------------------------------------------------
function RagPanel() {
  const [q, setQ] = useState("Was ist Tool Calling?");
  const [out, setOut] = useState(null);
  const [sources, setSources] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const ask = () => run(async () => {
    setErr(null); setSources(null); setOut(null);
    try {
      const res = await fetch("/api/rag?question=" + encodeURIComponent(q));
      const body = await res.text();
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + body);
      setOut(body);
    } catch (e) {
      setErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)");
    }
  });

  const showSrc = () => run(async () => {
    setErr(null); setOut(null); setSources(null);
    try {
      const res = await fetch("/api/rag/sources?question=" + encodeURIComponent(q) + "&topK=3");
      if (!res.ok) throw new Error("HTTP " + res.status);
      setSources(await res.json());
    } catch (e) {
      setErr(e.message);
    }
  });

  return (
    <Panel icon="manage_search" eyebrow="07_rag — knowledge-base.sh" title="RAG · Wissensspeicher"
      hint={<>Antwort auf Basis abgerufener Dokumente. <span className="inline-code">GET /api/rag</span> (Antwort) bzw. <span className="inline-code">/api/rag/sources</span> (nur Quellen, ohne API-Key).</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="quiz" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row">
        <Button icon="chat" busy={busy} onClick={ask}>ask</Button>
        <Button variant="outlined" icon="format_list_bulleted" busy={busy} onClick={showSrc}>sources</Button>
      </div>
      <ConsoleOut text={out} />
      {sources && (
        <div>
          {sources.map((s, i) => (
            <div className="src" key={i} style={{ animationDelay: (i * .08) + "s" }}>
              <div className="src-head">
                <span className="src-score">score {Number(s.score).toFixed(3)}</span>
                <span className="src-file">{s.source}</span>
              </div>
              <div className="src-text">{s.text}</div>
            </div>
          ))}
        </div>
      )}
      <ConsoleOut text={err} error />
    </Panel>
  );
}

// --- DB query -------------------------------------------------------------
function DbPanel() {
  const [q, setQ] = useState("Wie viele Versicherte haben E11.9?");
  const [out, setOut] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const ask = () => run(async () => {
    setErr(null); setOut(null);
    try {
      const res = await fetch("/api/db/ask?question=" + encodeURIComponent(q));
      const body = await res.text();
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + body);
      setOut(body);
    } catch (e) {
      setErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt und die DB befüllt?)");
    }
  });

  return (
    <Panel icon="database" eyebrow="14_db_query — ask-database.sh" title="DB-Abfrage · Frag deine Datenbank"
      hint={<>Natürlichsprachliche Aggregations-Frage gegen den eGK-Datenbestand. Das Modell nutzt geprüfte Abfrage-Tools bzw. ein abgesichertes SELECT. Ruft <span className="inline-code">GET /api/db/ask</span> auf.</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="search" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="bolt" busy={busy} onClick={ask}>ask</Button></div>
      <ConsoleOut text={out} />
      <ConsoleOut text={err} error />
    </Panel>
  );
}

const PANELS = { gateway: GatewayPanel, structured: StructuredPanel, tools: ToolsPanel, rag: RagPanel, db: DbPanel };
Object.assign(window, { PANELS });
