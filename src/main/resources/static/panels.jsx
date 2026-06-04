// Spring AI Developer Console — feature panels with real API calls.
const { useState } = React;

function Panel({ icon, eyebrow, title, hint, children }) {
  return (
    <section className="panel">
      <div className="panel-banner"></div>
      <div className="tabstrip"><span className="tab active">{eyebrow}</span></div>
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
  return (
    <div className={"console-block" + (error ? " error" : "")}>
      <div className="term-bar">
        <span className="wd r"></span><span className="wd y"></span><span className="wd g"></span>
        <span className="tb-label">bash — output</span>
      </div>
      <div className={"console" + (error ? " error" : "")}>{text}</div>
    </div>
  );
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
  const [q, setQ] = useState("Ist die KVNR A123456780 gültig?");
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
    <Panel icon="construction" eyebrow="06_tool_calling — egk-checks.sh" title="Tool Calling · eGK-Einzelsatz-Checks"
      hint={<>Das Modell wählt das passende Prüf-Tool: KVNR-/Luhn-Prüfziffer, eGK-Kartenstatus, ICD-10 auflösen, Zertifikats-Gültigkeit. Ruft <span className="inline-code">GET /api/tools</span> auf.</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="badge" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="play_arrow" busy={busy} onClick={go}>run</Button></div>
      <ConsoleOut text={out} />
      <ConsoleOut text={err} error />
    </Panel>
  );
}

// --- RAG ------------------------------------------------------------------
function RagPanel() {
  const [q, setQ] = useState("Was bedeutet der eGK-Status GESPERRT?");
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
    <Panel icon="manage_search" eyebrow="07_rag — telematik-wissen.sh" title="RAG · Telematik-Wissensspeicher"
      hint={<>Antwort auf Basis abgerufener Telematik-/eGK-Fachbegriffe (KVNR, Kartenstatus, Zertifikatstypen, ICD-10). <span className="inline-code">GET /api/rag</span> (Antwort) bzw. <span className="inline-code">/api/rag/sources</span> (nur Quellen, ohne API-Key).</>}>
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

// --- Evaluator-Optimizer (F16) --------------------------------------------
function EvaluatorPanel() {
  const [q, setQ] = useState("Wie viele Versicherte sind älter als 65 Jahre?");
  const [out, setOut] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const go = () => run(async () => {
    setErr(null); setOut(null);
    try {
      const res = await fetch("/api/evaluator/sql?question=" + encodeURIComponent(q));
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + (await res.text()));
      setOut(await res.json());
    } catch (e) {
      setErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)");
    }
  });

  return (
    <Panel icon="autorenew" eyebrow="16_evaluator_optimizer — self-correct.sh" title="Evaluator-Optimizer · Selbstkorrigierendes SQL"
      hint={<>Generator erzeugt SQL, ein Richter bewertet es gegen das Schema; bei Mängeln fließt die Kritik in einen neuen Versuch – bis akzeptiert oder Limit. Ruft <span className="inline-code">GET /api/evaluator/sql</span> auf.</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="rule" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="play_arrow" busy={busy} onClick={go}>optimize</Button></div>
      {out && (
        <div>
          <div className="viz" style={{ gridTemplateColumns: "1fr 1fr" }}>
            <div className="viz-card">
              <div className="vc-label">Ergebnis</div>
              <span className="cat-badge" style={{ background: out.success ? "#1E7D45" : "#BA1A1A" }}>
                {out.success ? "akzeptiert" : "nicht konvergiert"}
              </span>
            </div>
            <div className="viz-card">
              <div className="vc-label">Iterationen</div>
              <span className="cat-badge" style={{ background: "#1B4D89" }}>{out.attempts.length}</span>
            </div>
          </div>
          {out.attempts.map((a) => (
            <div className="src" key={a.iteration} style={{ animationDelay: ((a.iteration - 1) * .08) + "s" }}>
              <div className="src-head">
                <span className="src-score" style={{ background: a.evaluation.valid ? "#B7F0C9" : "#FFDAD6", color: a.evaluation.valid ? "#00210F" : "#410002" }}>
                  #{a.iteration} {a.evaluation.valid ? "valid" : "verworfen"}
                </span>
              </div>
              <div className="console" style={{ marginTop: 8 }}>{a.sql}</div>
              {!a.evaluation.valid && <div className="src-text" style={{ marginTop: 8 }}>↳ {a.evaluation.feedback}</div>}
            </div>
          ))}
        </div>
      )}
      <ConsoleOut text={err} error />
    </Panel>
  );
}

// --- pgvector (F17) -------------------------------------------------------
function PgVectorPanel() {
  const [info, setInfo] = useState(null);
  const [text, setText] = useState("Die eGK trägt die Zertifikate EGK_AUT und EGK_ENC.");
  const [addCat, setAddCat] = useState("egk");
  const [query, setQuery] = useState("Welche Zertifikate liegen auf der Karte?");
  const [searchCat, setSearchCat] = useState("");
  const [hits, setHits] = useState(null);
  const [msg, setMsg] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const loadInfo = () => run(async () => {
    setErr(null);
    try {
      const res = await fetch("/api/pgvector/info");
      if (!res.ok) throw new Error("HTTP " + res.status);
      setInfo((await res.json()).vectorStore);
    } catch (e) { setErr(e.message); }
  });

  const add = () => run(async () => {
    setErr(null); setMsg(null);
    try {
      const res = await fetch("/api/pgvector/documents?text=" + encodeURIComponent(text)
        + "&category=" + encodeURIComponent(addCat), { method: "POST" });
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + (await res.text()));
      const body = await res.json();
      setMsg("Abgelegt: id=" + body.id + " (category=" + body.category + ")");
    } catch (e) { setErr(e.message); }
  });

  const search = () => run(async () => {
    setErr(null); setHits(null);
    try {
      let url = "/api/pgvector/search?query=" + encodeURIComponent(query) + "&topK=3";
      if (searchCat.trim()) url += "&category=" + encodeURIComponent(searchCat.trim());
      const res = await fetch(url);
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + (await res.text()));
      setHits(await res.json());
    } catch (e) { setErr(e.message); }
  });

  return (
    <Panel icon="database" eyebrow="17_pgvector — persistent-store.sh" title="pgvector · Persistenter VectorStore"
      hint={<>Derselbe <span className="inline-code">VectorStore</span>-Code, aber persistent in PostgreSQL – mit Metadaten-Filter auf <span className="inline-code">category</span>. Ohne <span className="inline-code">postgres</span>-Profil läuft der In-Memory-Store. Endpunkte: <span className="inline-code">/api/pgvector/info · /documents · /search</span>.</>}>
      <div className="row"><Button variant="outlined" icon="info" busy={busy} onClick={loadInfo}>info</Button>
        {info && <span className="stat-chip">aktiv: {info}</span>}</div>

      <label className="field-label" style={{ marginTop: 18 }}>Dokument ablegen</label>
      <div className="field"><textarea value={text} onChange={e => setText(e.target.value)} /></div>
      <div className="row">
        <div className="field" style={{ flex: "0 0 180px" }}><Icon name="label" /><input value={addCat} onChange={e => setAddCat(e.target.value)} placeholder="category" /></div>
        <Button icon="add" busy={busy} onClick={add}>add</Button>
      </div>
      {msg && <ConsoleOut text={msg} />}

      <label className="field-label" style={{ marginTop: 18 }}>Ähnlichkeitssuche</label>
      <div className="field"><Icon name="search" /><input value={query} onChange={e => setQuery(e.target.value)} /></div>
      <div className="row">
        <div className="field" style={{ flex: "0 0 180px" }}><Icon name="filter_alt" /><input value={searchCat} onChange={e => setSearchCat(e.target.value)} placeholder="category (optional)" /></div>
        <Button icon="travel_explore" busy={busy} onClick={search}>search</Button>
      </div>
      {hits && (hits.length ? hits.map((h, i) => (
        <div className="src" key={i} style={{ animationDelay: (i * .08) + "s" }}>
          <div className="src-head">
            <span className="src-score">score {Number(h.score).toFixed(3)}</span>
            <span className="src-file">category: {h.category}</span>
          </div>
          <div className="src-text">{h.text}</div>
        </div>
      )) : <ConsoleOut text="Keine Treffer." />)}
      <ConsoleOut text={err} error />
    </Panel>
  );
}

const PANELS = {
  gateway: GatewayPanel, structured: StructuredPanel, tools: ToolsPanel, rag: RagPanel,
  db: DbPanel, evaluator: EvaluatorPanel, pgvector: PgVectorPanel,
};
Object.assign(window, { PANELS });
