// Spring AI Developer Console — shared primitives + chrome.

function Icon({ name, className, style }) {
  return <span className={"material-symbols-rounded" + (className ? " " + className : "")} style={style}>{name}</span>;
}

function Button({ variant = "filled", icon, busy, children, ...rest }) {
  return (
    <button className={"btn " + variant} disabled={busy || rest.disabled} {...rest}>
      {busy ? <Icon name="progress_activity" className="spin" /> : (icon ? <Icon name={icon} /> : null)}
      {children}
    </button>
  );
}

function ThemeSwitch() {
  const [theme, setTheme] = React.useState(
    () => document.documentElement.dataset.theme || "c"
  );
  React.useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("console-theme", theme);
  }, [theme]);
  return (
    <div className="theme-switch" role="group" aria-label="Design-Theme">
      <button className={theme === "c" ? "on" : ""} onClick={() => setTheme("c")}>
        <Icon name="bubble_chart" />Soft
      </button>
      <button className={theme === "b" ? "on" : ""} onClick={() => setTheme("b")}>
        <Icon name="terminal" />Terminal
      </button>
    </div>
  );
}

// Reihenfolge + Anzeigenamen der Versions-Badges. Die Werte selbst stammen aus
// /actuator/info (gespeist aus der pom.xml), Schlüssel = info.versions.<key>.
const VERSION_CHIPS = [
  { key: "spring-boot", label: "Spring Boot" },
  { key: "spring-ai",   label: "Spring AI" },
  { key: "java",        label: "Java" },
];

function TopAppBar() {
  // Versionen einmalig vom Actuator-Info-Endpoint laden (kein LoaderBus: still).
  const [versions, setVersions] = React.useState(null);
  React.useEffect(() => {
    fetch("/actuator/info")
      .then(r => r.ok ? r.json() : Promise.reject(r.status))
      .then(info => setVersions(info.versions || {}))
      .catch(() => setVersions({}));
  }, []);
  return (
    <header className="appbar">
      <div className="win-dots"><span className="wd r"></span><span className="wd y"></span><span className="wd g"></span></div>
      <div className="brand">
        <div className="badge" aria-hidden="true">
          {/* Eigenständiges Backend-Emblem: gestapelte Service-/Daten-Layer mit Status-LEDs. */}
          <svg viewBox="0 0 24 24" role="img" aria-label="Backend Working Group">
            <rect x="3" y="3.5" width="18" height="5"   rx="1.6" fill="rgba(255,255,255,.96)" />
            <rect x="3" y="10"  width="18" height="5"   rx="1.6" fill="rgba(255,255,255,.78)" />
            <rect x="3" y="16.5" width="13" height="4.2" rx="1.6" fill="rgba(255,255,255,.56)" />
            <circle cx="6.6" cy="6"    r="1" fill="#4fd6b6" />
            <circle cx="6.6" cy="12.5" r="1" fill="#4fd6b6" />
          </svg>
        </div>
        <div className="word">
          <span className="brand-eyebrow">WorkingGroup <span className="be-tag">-Backend-</span></span>
          <span className="brand-title">Spring-AI <span className="accent">Exploration</span></span>
        </div>
      </div>
      <div className="spacer"></div>
      <ThemeSwitch />
      <div className="chips">
        {versions && VERSION_CHIPS
          .filter(c => versions[c.key])
          .map(c => (
            <span key={c.key} className="stat-chip">{c.label} {versions[c.key]}</span>
          ))}
      </div>
      <div className="key-pill"><span className="dot"></span>Claude · API-Key aktiv</div>
    </header>
  );
}

const FEATURES = [
  { id: "gateway",    icon: "hub",            name: "Gate-Decider",      endpoint: "/api/gateway" },
  { id: "structured", icon: "account_tree",   name: "Structured Output", endpoint: "/api/tickets/analyze" },
  { id: "tools",      icon: "construction",   name: "Tool Calling",      endpoint: "/api/tools" },
  { id: "rag",        icon: "manage_search",  name: "RAG",               endpoint: "/api/rag" },
  { id: "advisors",   icon: "tune",           name: "Advisors",          endpoint: "/api/advisors" },
  { id: "db",         icon: "database",       name: "DB-Abfrage",        endpoint: "/api/db/ask" },
  { id: "evaluator",  icon: "autorenew",      name: "Evaluator-Optimizer", endpoint: "/api/evaluator/sql" },
  { id: "pgvector",   icon: "database",       name: "pgvector",          endpoint: "/api/pgvector" },
  { id: "docsrag",    icon: "cloud_sync",     name: "Docs-RAG Pipeline", endpoint: "/api/docs-rag" },
  { id: "moderation", icon: "shield",         name: "Moderation",        endpoint: "/api/moderation" },
];

// Gruppierung der Nav-Items in drei einklappbare Themenblöcke.
const NAV_GROUPS = [
  { label: "Agentic Patterns",            icon: "smart_toy",     ids: ["gateway", "tools", "evaluator"] },
  { label: "Retrieval & Daten (pgvector)", icon: "database",      ids: ["rag", "docsrag", "db", "pgvector"] },
  { label: "LLM-Bausteine",               icon: "deployed_code", ids: ["structured", "moderation", "advisors"] },
];

const FEATURE_BY_ID = Object.fromEntries(FEATURES.map(f => [f.id, f]));

function NavDrawer({ active, onSelect }) {
  // Alle Gruppen initial offen; Set hält die eingeklappten Labels.
  const [collapsed, setCollapsed] = React.useState(() => new Set());
  const toggle = (label) => setCollapsed(prev => {
    const next = new Set(prev);
    next.has(label) ? next.delete(label) : next.add(label);
    return next;
  });
  return (
    <nav className="nav">
      {NAV_GROUPS.map(group => {
        const open = !collapsed.has(group.label);
        return (
          <div key={group.label} className={"nav-group" + (open ? " open" : "")}>
            <button
              className="nav-group-head"
              onClick={() => toggle(group.label)}
              aria-expanded={open}
            >
              <Icon name={group.icon} className="ngh-icon" />
              <span className="ngh-label">{group.label}</span>
              <Icon name="expand_more" className="ngh-chevron" />
            </button>
            <div className="nav-group-items">
              {group.ids.map(id => FEATURE_BY_ID[id]).map(f => (
                <button
                  key={f.id}
                  className={"nav-item" + (active === f.id ? " active" : "")}
                  onClick={() => onSelect(f.id)}
                >
                  <Icon name={f.icon} />
                  <span className="ni-text">
                    <span className="ni-name">{f.name}</span>
                    <span className="ni-end">{f.endpoint}</span>
                  </span>
                </button>
              ))}
            </div>
          </div>
        );
      })}
    </nav>
  );
}

// --- Globaler LLM-Loader (Spring Orbit) -----------------------------------
// Ein winziger Bus zählt parallele Requests. useFetch() ruft inc()/dec() auf,
// sodass JEDER vorhandene und künftige run(...)-Aufruf den Loader auslöst,
// ohne dass ein einzelnes Panel angefasst werden muss.
const LoaderBus = {
  _count: 0,
  _subs: new Set(),
  inc() { this._count++; this._emit(); },
  dec() { this._count = Math.max(0, this._count - 1); this._emit(); },
  subscribe(fn) { this._subs.add(fn); return () => this._subs.delete(fn); },
  _emit() { const active = this._count > 0; this._subs.forEach(fn => fn(active)); },
};

// Einmal im App-Root gemountet. Legt ein fixes Overlay über den ganzen Screen:
// backdrop-filter verschwimmt alles dahinter, der Orbit-Spinner bleibt scharf
// im Fokus. Rein wie raus rein über CSS-Transitions (siehe styles.css).
function GlobalLoader() {
  const [active, setActive] = React.useState(false);
  React.useEffect(() => LoaderBus.subscribe(setActive), []);
  return (
    <div className={"global-loader" + (active ? " active" : "")} aria-hidden={!active}>
      <div className="gl-card" role="status" aria-label="LLM denkt nach">
        <div className="gl-rings">
          <span className="gl-ring r1"></span>
          <span className="gl-ring r2"></span>
          <span className="gl-ring r3"></span>
          <span className="gl-orbit"><i className="gl-electron"></i></span>
          <Icon name="eco" className="gl-core" />
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { Icon, Button, TopAppBar, NavDrawer, FEATURES, NAV_GROUPS, LoaderBus, GlobalLoader });
