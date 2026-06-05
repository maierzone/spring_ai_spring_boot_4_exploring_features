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

function TopAppBar() {
  return (
    <header className="appbar">
      <div className="win-dots"><span className="wd r"></span><span className="wd y"></span><span className="wd g"></span></div>
      <div className="brand">
        <div className="badge"><Icon name="terminal" /></div>
        <div className="word">Spring AI <span className="accent">Console</span></div>
      </div>
      <div className="spacer"></div>
      <ThemeSwitch />
      <div className="chips">
        <span className="stat-chip">Spring Boot 4.0.6</span>
        <span className="stat-chip">Spring AI 2.0.0-M8</span>
        <span className="stat-chip">Java 21</span>
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
  { id: "db",         icon: "database",       name: "DB-Abfrage",        endpoint: "/api/db/ask" },
  { id: "evaluator",  icon: "autorenew",      name: "Evaluator-Optimizer", endpoint: "/api/evaluator/sql" },
  { id: "pgvector",   icon: "database",       name: "pgvector",          endpoint: "/api/pgvector" },
  { id: "docsrag",    icon: "cloud_sync",     name: "Docs-RAG Pipeline", endpoint: "/api/docs-rag" },
  { id: "moderation", icon: "shield",         name: "Moderation",        endpoint: "/api/moderation" },
];

function NavDrawer({ active, onSelect }) {
  return (
    <nav className="nav">
      <div className="nav-label">Features</div>
      {FEATURES.map(f => (
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
    </nav>
  );
}

Object.assign(window, { Icon, Button, TopAppBar, NavDrawer, FEATURES });
