// Spring AI Developer Console — feature panels (fake interactive responses).
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

function useFakeRun() {
  const [busy, setBusy] = useState(false);
  const run = (fn, ms = 950) => {
    setBusy(true);
    setTimeout(() => { fn(); setBusy(false); }, ms);
  };
  return [busy, run];
}

// --- Gate-Decider ---------------------------------------------------------
function GatewayPanel() {
  const [q, setQ] = useState("Wie viele Versicherte haben E11.9?");
  const [out, setOut] = useState(null);
  const [busy, run] = useFakeRun();
  const go = () => run(() => setOut({
    route: "db_query",
    answer: "1.284 Versicherte sind mit dem ICD-Code E11.9 (Diabetes mellitus Typ 2) erfasst."
  }));
  return (
    <Panel icon="hub" eyebrow="00_gate_decider — route.sh" title="Gate-Decider · KI-Router"
      hint={<>Ein Eingang für alles: Die KI entscheidet, welches Feature zuständig ist, und delegiert an dessen Endpunkt. Ruft <span className="inline-code">GET /api/gateway</span> auf.</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="forum" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="alt_route" busy={busy} onClick={go}>route</Button></div>
      {out && (
        <div className="console"><span className="route">→ Route:</span> {out.route}{"\n\n"}{out.answer}</div>
      )}
    </Panel>
  );
}

// --- Structured Output ----------------------------------------------------
const CAT_COLORS = { BUG: "#ef4444", FEATURE_REQUEST: "#3b82f6", QUESTION: "#a855f7", BILLING: "#f59e0b", OTHER: "#64748b" };
const PRIO = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const PRIO_COLORS = { LOW: "#22c55e", MEDIUM: "#eab308", HIGH: "#f97316", URGENT: "#ef4444" };
const SENT = { NEGATIVE: { pos: "16%", color: "#ef4444" }, NEUTRAL: { pos: "50%", color: "#64748b" }, POSITIVE: { pos: "84%", color: "#1E7D45" } };

function StructuredPanel() {
  const [text, setText] = useState("Nach dem letzten Update kann ich mich nicht mehr einloggen. Das ist extrem ärgerlich, ich brauche dringend eine Lösung!");
  const [a, setA] = useState(null);
  const [busy, run] = useFakeRun();
  const analyze = () => run(() => setA({
    category: "BUG", priority: "HIGH", customerSentiment: "NEGATIVE",
    summary: "Login nach Update nicht mehr möglich; Kunde verärgert und benötigt dringend eine Lösung."
  }));
  const sent = a ? (SENT[a.customerSentiment] || SENT.NEUTRAL) : null;
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
            <span className="cat-badge" style={{ background: CAT_COLORS[a.category] }}>{a.category}</span>
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
    </Panel>
  );
}

// --- Tool Calling ---------------------------------------------------------
function ToolsPanel() {
  const [q, setQ] = useState("Wie viele Monitore sind auf Lager?");
  const [out, setOut] = useState(null);
  const [busy, run] = useFakeRun();
  const go = () => run(() => setOut("Aktuell sind 42 Monitore auf Lager (Artikel \"Dell UltraSharp 27\")."));
  return (
    <Panel icon="construction" eyebrow="06_tool_calling — product-catalog.sh" title="Tool Calling · Produktkatalog"
      hint={<>Das Modell ruft bei Bedarf den Katalog-Service auf. Ruft <span className="inline-code">GET /api/tools</span> auf.</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="inventory_2" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="play_arrow" busy={busy} onClick={go}>run</Button></div>
      {out && <div className="console"><span className="dim">⚙ getProductStock("Monitor")</span>{"\n\n"}{out}</div>}
    </Panel>
  );
}

// --- RAG ------------------------------------------------------------------
const SOURCES = [
  { score: 0.842, file: "spring-ai-faq.md", text: "Tool Calling erlaubt es dem Modell, während der Antwortgenerierung annotierte Java-Methoden aufzurufen." },
  { score: 0.671, file: "spring-ai-faq.md", text: "RAG steht für Retrieval Augmented Generation: passende Dokumente werden als Kontext angehängt." },
  { score: 0.604, file: "spring-ai-faq.md", text: "Advisors sind das Erweiterungskonzept von Spring AI und klinken sich in den Anfrage-/Antwortfluss ein." },
];
function RagPanel() {
  const [q, setQ] = useState("Was ist Tool Calling?");
  const [out, setOut] = useState(null);
  const [busy, run] = useFakeRun();
  const ask = () => run(() => setOut({ type: "answer", text: "Tool Calling lässt das Modell während der Antwort eigene Java-Methoden aufrufen — etwa für aktuelle Daten aus Datenbanken oder APIs, statt zu raten." }));
  const sources = () => run(() => setOut({ type: "sources" }), 600);
  return (
    <Panel icon="manage_search" eyebrow="07_rag — knowledge-base.sh" title="RAG · Wissensspeicher"
      hint={<>Antwort auf Basis abgerufener Dokumente. <span className="inline-code">GET /api/rag</span> (Antwort) bzw. <span className="inline-code">/api/rag/sources</span> (nur Quellen, ohne API-Key).</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="quiz" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row">
        <Button icon="chat" busy={busy} onClick={ask}>ask</Button>
        <Button variant="outlined" icon="format_list_bulleted" busy={busy} onClick={sources}>sources</Button>
      </div>
      {out && out.type === "answer" && <div className="console">{out.text}</div>}
      {out && out.type === "sources" && (
        <div>
          {SOURCES.map((s, i) => (
            <div className="src" key={i} style={{ animationDelay: (i * .08) + "s" }}>
              <div className="src-head"><span className="src-score">score {s.score.toFixed(3)}</span><span className="src-file">{s.file}</span></div>
              <div className="src-text">{s.text}</div>
            </div>
          ))}
        </div>
      )}
    </Panel>
  );
}

// --- DB query -------------------------------------------------------------
function DbPanel() {
  const [q, setQ] = useState("Wie viele Versicherte haben E11.9?");
  const [out, setOut] = useState(null);
  const [busy, run] = useFakeRun();
  const ask = () => run(() => setOut({
    sql: "SELECT COUNT(*) FROM versicherte v JOIN diagnosen d ON d.vid = v.id WHERE d.icd = 'E11.9';",
    answer: "1.284 Versicherte sind mit E11.9 (Diabetes mellitus Typ 2) erfasst."
  }));
  return (
    <Panel icon="database" eyebrow="14_db_query — ask-database.sh" title="DB-Abfrage · Frag deine Datenbank"
      hint={<>Natürlichsprachliche Aggregations-Frage gegen den eGK-Datenbestand. Das Modell nutzt geprüfte Abfrage-Tools bzw. ein abgesichertes SELECT. Ruft <span className="inline-code">GET /api/db/ask</span> auf.</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="search" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="bolt" busy={busy} onClick={ask}>ask</Button></div>
      {out && <div className="console"><span className="dim">{out.sql}</span>{"\n\n"}<span className="ok">{out.answer}</span></div>}
    </Panel>
  );
}

const PANELS = { gateway: GatewayPanel, structured: StructuredPanel, tools: ToolsPanel, rag: RagPanel, db: DbPanel };
Object.assign(window, { PANELS });
