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
    setBusy(true); LoaderBus.inc();
    try { await fn(); } finally { setBusy(false); LoaderBus.dec(); }
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

// --- Call-Flow-Kette (echter Methoden-Aufruf-Flow, wie im Debugger) --------
// Plaettet den CallNode-Baum in Zeilen mit ASCII-Verzweigungen (├─ / └─ / │),
// damit die Einrueckung in der Monospace-Darstellung exakt ausgerichtet bleibt.
function flattenFlow(node, prefix, isLast, isRoot, out) {
  out.push({
    branch: isRoot ? "" : prefix + (isLast ? "└─ " : "├─ "),
    method: node.method,
    location: node.location,
  });
  const childPrefix = isRoot ? "" : prefix + (isLast ? "   " : "│  ");
  const kids = node.calls || [];
  kids.forEach((c, i) => flattenFlow(c, childPrefix, i === kids.length - 1, false, out));
  return out;
}

function FlowChain({ flow }) {
  if (!flow || !flow.length) return null;
  const lines = [];
  flow.forEach(root => flattenFlow(root, "", true, true, lines));
  return (
    <div className="console-block">
      <div className="term-bar">
        <span className="wd r"></span><span className="wd y"></span><span className="wd g"></span>
        <span className="tb-label">call-flow — debugger trace</span>
      </div>
      <div className="flow">
        {lines.map((l, i) => (
          <div className="flow-row" key={i} style={{ animationDelay: (i * .03) + "s" }}>
            <span className="flow-branch">{l.branch}</span>
            <span className="flow-method">{l.method}</span>
            {l.location && <span className="flow-loc">{l.location}</span>}
          </div>
        ))}
      </div>
    </div>
  );
}

// Badge: Ressourcenverbrauch einer Anfrage (Token + Verarbeitungszeit + Kosten) auf einen Blick.
function MetricsBadge({ m }) {
  if (!m) return null;
  const hasTokens = m.totalTokens != null;
  const hasCost = m.costUsd != null;
  return (
    <div className="metrics-badge" title="Spring AI Observability · gen_ai.client.token.usage + Verarbeitungszeit + Kosten (ModelPricing)">
      <div className="mb-item">
        <Icon name="schedule" />
        <span className="mb-val">{m.latencyMs}<span className="mb-unit">ms</span></span>
        <span className="mb-label">Verarbeitung</span>
      </div>
      <div className="mb-sep"></div>
      <div className="mb-item">
        <Icon name="toll" />
        <span className="mb-val">{hasTokens ? m.totalTokens : "—"}</span>
        <span className="mb-label">Tokens gesamt</span>
      </div>
      <div className="mb-sep"></div>
      <div className="mb-item">
        <Icon name="payments" />
        <span className="mb-val">{hasCost ? "$" + m.costUsd.toFixed(6) : "—"}</span>
        <span className="mb-label">Kosten</span>
      </div>
      {hasTokens && (
        <div className="mb-split">
          <span className="mb-chip in"><Icon name="login" />{m.inputTokens} in</span>
          <span className="mb-chip out"><Icon name="logout" />{m.outputTokens} out</span>
        </div>
      )}
    </div>
  );
}

// --- Gate-Decider ---------------------------------------------------------
function GatewayPanel() {
  const [q, setQ] = useState("Wie viele Versicherte haben E11.9?");
  const [out, setOut] = useState(null);
  const [flow, setFlow] = useState(null);
  const [metrics, setMetrics] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const go = () => run(async () => {
    setErr(null); setOut(null); setFlow(null); setMetrics(null);
    try {
      const res = await fetch("/api/gateway?question=" + encodeURIComponent(q));
      const body = await res.json();
      if (!res.ok) throw new Error("HTTP " + res.status);
      setOut("→ Route: " + body.route + "\n\n" + body.antwort);
      setFlow(body.flow);
      setMetrics(body.metrics);
    } catch (e) {
      setErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)");
    }
  });

  return (
    <Panel icon="hub" eyebrow="00_gate_decider — route.sh" title="Gate-Decider · KI-Router"
      hint={<>Ein Eingang für alles: Die KI entscheidet, welches Feature zuständig ist, und delegiert an dessen Endpunkt. Ruft <span className="inline-code">GET /api/gateway</span> auf – die <b>Call-Flow</b>-Kette unten zeigt den tatsächlich durchlaufenen Methoden-Aufruf-Pfad (wie im Debugger), das <b>Badge</b> den Token-Verbrauch &amp; die Verarbeitungszeit dieser Anfrage.</>}>
      <label className="field-label">Frage</label>
      <div className="field"><Icon name="forum" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="alt_route" busy={busy} onClick={go}>route</Button></div>
      <MetricsBadge m={metrics} />
      <ConsoleOut text={out} />
      <FlowChain flow={flow} />
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
function SourceList({ items }) {
  if (!items) return null;
  if (items.length === 0) return <ConsoleOut text="Keine Treffer." />;
  return (
    <div>
      {items.map((s, i) => (
        <div className="src" key={i} style={{ animationDelay: (i * .08) + "s" }}>
          <div className="src-head">
            <span className="src-score">score {Number(s.score).toFixed(3)}</span>
            <span className="src-file">{s.source}</span>
          </div>
          <div className="src-text">{s.text}</div>
        </div>
      ))}
    </div>
  );
}

function RagPanel() {
  const [q, setQ] = useState("Was bedeutet der eGK-Status GESPERRT?");
  const [out, setOut] = useState(null);
  const [sources, setSources] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  // Laufzeit-Import eigener Dokumente (getrennter Store, /api/rag/import/*).
  const [imp, setImp] = useState(null);       // stats {dir, files, documents}
  const [impSrc, setImpSrc] = useState(null); // Treffer aus dem Import-Store
  const [impOut, setImpOut] = useState(null); // RAG-Antwort über die Importe
  const [impMsg, setImpMsg] = useState(null); // Status-/Erfolgsmeldung
  const [impErr, setImpErr] = useState(null);
  const fileRef = React.useRef(null);

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

  const resetImpOut = () => { setImpErr(null); setImpMsg(null); setImpSrc(null); setImpOut(null); };

  const scanFolder = () => run(async () => {
    resetImpOut();
    try {
      const res = await fetch("/api/rag/import/folder", { method: "POST" });
      const body = await res.json();
      if (!res.ok) throw new Error(body.message || ("HTTP " + res.status));
      setImp(body);
      setImpMsg(body.documents + " Dokument(e) aus " + body.files.length + " Datei(en) eingelesen.\nOrdner: " + body.dir);
    } catch (e) { setImpErr(e.message); }
  });

  const uploadFiles = () => run(async () => {
    resetImpOut();
    const input = fileRef.current;
    if (!input || !input.files.length) { setImpErr("Bitte zuerst Datei(en) wählen."); return; }
    const fd = new FormData();
    for (const f of input.files) fd.append("files", f);
    try {
      const res = await fetch("/api/rag/import/upload", { method: "POST", body: fd });
      const body = await res.json();
      if (!res.ok) throw new Error(body.message || ("HTTP " + res.status));
      setImp(body);
      setImpMsg(body.added + " neue Dokument(e) hinzugefügt. Import-Speicher gesamt: " + body.documents + ".");
      input.value = "";
    } catch (e) { setImpErr(e.message); }
  });

  const clearImp = () => run(async () => {
    resetImpOut();
    try {
      const res = await fetch("/api/rag/import/clear", { method: "POST" });
      setImp(await res.json());
      setImpMsg("Import-Speicher geleert.");
    } catch (e) { setImpErr(e.message); }
  });

  const askImp = () => run(async () => {
    resetImpOut();
    try {
      const res = await fetch("/api/rag/import/ask?question=" + encodeURIComponent(q));
      const body = await res.text();
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + body);
      setImpOut(body);
    } catch (e) { setImpErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)"); }
  });

  const srcImp = () => run(async () => {
    resetImpOut();
    try {
      const res = await fetch("/api/rag/import/sources?question=" + encodeURIComponent(q) + "&topK=8");
      if (!res.ok) throw new Error("HTTP " + res.status);
      setImpSrc(await res.json());
    } catch (e) { setImpErr(e.message); }
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
      <SourceList items={sources} />
      <ConsoleOut text={err} error />

      <div className="import-section">
        <label className="field-label">Eigene Dokumente importieren (getrennter Speicher · nur diese werden durchsucht)</label>
        <p className="hint">Lege <span className="inline-code">.md/.txt/.pdf</span> in den Server-Ordner und lies sie ein, oder lade Datei(en) direkt hoch. Danach durchsuchen <b>ask · Import</b> / <b>sources · Import</b> ausschliesslich die importierten Dokumente. Endpunkte <span className="inline-code">/api/rag/import/folder · /upload · /sources · /ask</span>. Für brauchbare Treffer in echten PDFs: <span className="inline-code">demo.rag.import-embedding=onnx</span> (lokales semantisches Embedding) – sonst arbeitet die Suche nicht-semantisch.</p>
        <div className="row">
          <Button icon="folder_open" busy={busy} onClick={scanFolder}>Ordner einlesen</Button>
          <Button variant="outlined" icon="delete_sweep" busy={busy} onClick={clearImp}>leeren</Button>
        </div>
        <div className="row">
          <input ref={fileRef} type="file" multiple accept=".md,.txt,.markdown,.pdf" className="file-input" />
          <Button variant="outlined" icon="upload_file" busy={busy} onClick={uploadFiles}>hochladen</Button>
        </div>
        {imp && (
          <div className="import-stats">
            <Icon name="inventory_2" /> {imp.documents} Dokument(e) aus {imp.files.length} Quelle(n)
            {imp.embedding && <span className="emb-chip"><Icon name="network_intelligence" />{imp.embedding}</span>}
            {imp.files.length > 0 && <span className="src-file"> · {imp.files.join(", ")}</span>}
          </div>
        )}
        <div className="row">
          <Button icon="chat" busy={busy} onClick={askImp}>ask · Import</Button>
          <Button variant="outlined" icon="format_list_bulleted" busy={busy} onClick={srcImp}>sources · Import</Button>
        </div>
        {impMsg && <ConsoleOut text={impMsg} />}
        <ConsoleOut text={impOut} />
        <SourceList items={impSrc} />
        <ConsoleOut text={impErr} error />
      </div>
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

// --- Docs-RAG Pipeline (F18) ----------------------------------------------
function DocsRagPanel() {
  const [stats, setStats] = React.useState({ total: 0, incoming: 0, processed: 0, error: 0, running: false, batchSize: 1 });
  const [batch, setBatch] = React.useState(1);
  const [msg, setMsg] = React.useState(null);
  const [err, setErr] = React.useState(null);
  const [busy, run] = useFetch();

  const HINT = "\n(Läuft die App mit Profil 'specs'? Endpunkte gibt es nur dort.)";

  const loadStats = async () => {
    try {
      const res = await fetch("/api/docs-rag/stats");
      if (!res.ok) throw new Error("HTTP " + res.status);
      setStats(await res.json());
      setErr(null);
    } catch (e) { setErr(e.message + HINT); }
  };

  // Live-Polling der 4 Zähler.
  React.useEffect(() => {
    loadStats();
    const t = setInterval(loadStats, 2000);
    return () => clearInterval(t);
  }, []);

  const post = (path, then) => run(async () => {
    setErr(null); setMsg(null);
    try {
      const res = await fetch(path, { method: "POST" });
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + (await res.text()));
      then(await res.json());
    } catch (e) { setErr(e.message + HINT); }
  });

  const seed = () => post("/api/docs-rag/seed", (r) => { setMsg(r.inserted + " neue Dokumente in den Katalog aufgenommen."); setStats(s => ({ ...s, ...r })); });
  const start = () => post("/api/docs-rag/start?batch=" + batch, setStats);
  const stop = () => post("/api/docs-rag/stop", setStats);

  const cards = [
    { label: "TOTAL · gemspec", value: stats.total, color: "#1B4D89", icon: "inventory_2" },
    { label: "incoming", value: stats.incoming, color: "#9A6700", icon: "schedule" },
    { label: "processed", value: stats.processed, color: "#1E7D45", icon: "task_alt" },
    { label: "error", value: stats.error, color: "#BA1A1A", icon: "error" },
  ];
  const done = stats.total ? Math.round(((stats.processed + stats.error) / stats.total) * 100) : 0;

  return (
    <Panel icon="cloud_sync" eyebrow="18_docs_rag — pipeline.sh" title="Docs-RAG Pipeline · gemspec live-Ingestion"
      hint={<>Lädt den gemspec-Katalog (<span className="inline-code">sitemap.xml</span>) und verarbeitet die Spec-PDFs batchweise live in den pgvector-Speicher. Status pro Dokument: incoming → processed | error. Endpunkte <span className="inline-code">/api/docs-rag/seed · /start · /stop · /stats</span>. Nur mit Profil <span className="inline-code">specs</span>.</>}>
      <div className="viz" style={{ gridTemplateColumns: "repeat(4, 1fr)" }}>
        {cards.map(c => (
          <div className="viz-card" key={c.label}>
            <div className="vc-label"><Icon name={c.icon} /> {c.label}</div>
            <div style={{ fontSize: 34, fontWeight: 700, color: c.color, fontFamily: "'JetBrains Mono', monospace", lineHeight: 1.1 }}>{c.value}</div>
          </div>
        ))}
      </div>

      <div className="gauge-track" style={{ marginTop: 16 }}>
        <div style={{ width: done + "%", height: "100%", borderRadius: "inherit", background: "linear-gradient(90deg,#1E7D45,#1B4D89)", transition: "width .4s" }}></div>
      </div>
      <div className="hint" style={{ marginTop: 6 }}>{done}% verarbeitet ({stats.processed + stats.error}/{stats.total})</div>

      <div className="row" style={{ marginTop: 18, alignItems: "center" }}>
        <div className="field" style={{ flex: "0 0 130px" }}>
          <Icon name="layers" />
          <select value={batch} onChange={e => setBatch(Number(e.target.value))} disabled={stats.running}
            style={{ background: "transparent", color: "inherit", border: "none", outline: "none", font: "inherit", width: "100%" }}>
            <option value={1}>Batch 1</option>
            <option value={5}>Batch 5</option>
            <option value={10}>Batch 10</option>
          </select>
        </div>
        {stats.running
          ? <Button variant="outlined" icon="pause" busy={busy} onClick={stop}>stop</Button>
          : <Button icon="play_arrow" busy={busy} onClick={start}>start</Button>}
        <Button variant="outlined" icon="cloud_download" busy={busy} disabled={stats.running} onClick={seed}>seed katalog</Button>
        <span className="stat-chip"><span className="dot" style={{ background: stats.running ? "#1E7D45" : "#9A6700" }}></span>{stats.running ? "läuft · Batch " + stats.batchSize : "pausiert"}</span>
      </div>
      {msg && <ConsoleOut text={msg} />}
      <ConsoleOut text={err} error />
    </Panel>
  );
}

// --- Moderation (F19) -----------------------------------------------------
function ModerationPanel() {
  const [text, setText] = useState("Erklaere kurz, was Content-Moderation ist.");
  const [out, setOut] = useState(null);
  const [verdict, setVerdict] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const ask = () => run(async () => {
    setErr(null); setOut(null); setVerdict(null);
    try {
      const res = await fetch("/api/moderation?message=" + encodeURIComponent(text));
      const body = await res.text();
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + body);
      setOut(body);
    } catch (e) {
      setErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)");
    }
  });

  const check = () => run(async () => {
    setErr(null); setOut(null); setVerdict(null);
    try {
      const res = await fetch("/api/moderation/check?text=" + encodeURIComponent(text));
      if (!res.ok) throw new Error("HTTP " + res.status + " – " + (await res.text()));
      setVerdict(await res.json());
    } catch (e) {
      setErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)");
    }
  });

  return (
    <Panel icon="shield" eyebrow="19_moderation — guardrail.sh" title="Moderation · Guardrail-Advisor"
      hint={<>Claude prüft sich selbst als Klassifikator: Eingaben werden <i>vor</i> dem Modellaufruf, Antworten <i>danach</i> gefiltert. <span className="inline-code">GET /api/moderation</span> (geschützter Chat) bzw. <span className="inline-code">/api/moderation/check</span> (reines Urteil, analog zu RAG-sources).</>}>
      <label className="field-label">Text</label>
      <div className="field"><textarea value={text} onChange={e => setText(e.target.value)} /></div>
      <div className="row">
        <Button icon="chat" busy={busy} onClick={ask}>ask</Button>
        <Button variant="outlined" icon="policy" busy={busy} onClick={check}>check</Button>
      </div>
      <ConsoleOut text={out} />
      {verdict && (
        <div className="viz" style={{ gridTemplateColumns: "1fr 1fr" }}>
          <div className="viz-card">
            <div className="vc-label">Urteil</div>
            <span className="cat-badge" style={{ background: verdict.flagged ? "#BA1A1A" : "#1E7D45" }}>
              {verdict.flagged ? "flagged" : "unbedenklich"}
            </span>
          </div>
          <div className="viz-card">
            <div className="vc-label">Kategorien</div>
            {verdict.categories && verdict.categories.length
              ? <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                  {verdict.categories.map(c => (
                    <span className="cat-badge" key={c} style={{ background: "#9A6700" }}>{c}</span>
                  ))}
                </div>
              : <span className="hint">–</span>}
          </div>
          {verdict.reason && (
            <div className="viz-card full">
              <div className="vc-label">Begründung</div>
              <div className="summary">{verdict.reason}</div>
            </div>
          )}
        </div>
      )}
      <ConsoleOut text={err} error />
    </Panel>
  );
}

// --- Advisors (Metriken & Kosten) -----------------------------------------
function AdvisorsPanel() {
  const [q, setQ] = useState("Erklaere kurz, was ein Advisor ist.");
  const [out, setOut] = useState(null);
  const [metrics, setMetrics] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, run] = useFetch();

  const go = () => run(async () => {
    setErr(null); setOut(null); setMetrics(null);
    try {
      const res = await fetch("/api/advisors?message=" + encodeURIComponent(q));
      const body = await res.json();
      if (!res.ok) throw new Error("HTTP " + res.status);
      setOut(body.answer);
      setMetrics(body.metrics);
    } catch (e) {
      setErr(e.message + "\n(Ist ANTHROPIC_API_KEY gesetzt?)");
    }
  });

  const val = (v, suffix) => (v === null || v === undefined) ? "n/a" : v + (suffix || "");
  const cost = (v) => (v === null || v === undefined) ? "n/a" : "$" + v.toFixed(6);

  return (
    <Panel icon="tune" eyebrow="10_advisors — metrics.sh" title="Advisors · Metriken & Kosten"
      hint={<>Der selbst geschriebene <span className="inline-code">MetricsLoggingAdvisor</span> misst Latenz und Token-Verbrauch und rechnet über eine Preis-Maptabelle (<span className="inline-code">ModelPricing</span>) die Kosten je Aufruf aus. <span className="inline-code">GET /api/advisors</span> liefert Antwort plus Metrik-Badge.</>}>
      <label className="field-label">Nachricht</label>
      <div className="field"><Icon name="forum" /><input value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="row"><Button icon="chat" busy={busy} onClick={go}>ask</Button></div>
      <ConsoleOut text={out} />
      {metrics && (
        <div className="viz" style={{ gridTemplateColumns: "repeat(3, 1fr)" }}>
          <div className="viz-card">
            <div className="vc-label">Tokens (in / out)</div>
            <div className="summary">{val(metrics.inputTokens)} / {val(metrics.outputTokens)}</div>
          </div>
          <div className="viz-card">
            <div className="vc-label">Verarbeitungszeit</div>
            <div className="summary">{val(metrics.latencyMs, " ms")}</div>
          </div>
          <div className="viz-card">
            <div className="vc-label">Kosten (USD)</div>
            <div className="summary">{cost(metrics.costUsd)}</div>
          </div>
          <div className="viz-card full">
            <div className="vc-label">Modell · Tokens gesamt</div>
            <div className="summary">{val(metrics.model)} · {val(metrics.totalTokens)}</div>
          </div>
        </div>
      )}
      <ConsoleOut text={err} error />
    </Panel>
  );
}

const PANELS = {
  gateway: GatewayPanel, structured: StructuredPanel, tools: ToolsPanel, rag: RagPanel,
  advisors: AdvisorsPanel,
  db: DbPanel, evaluator: EvaluatorPanel, pgvector: PgVectorPanel, docsrag: DocsRagPanel,
  moderation: ModerationPanel,
};
Object.assign(window, { PANELS });
