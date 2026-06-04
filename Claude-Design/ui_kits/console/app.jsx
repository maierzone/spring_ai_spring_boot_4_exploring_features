// Spring AI Developer Console — main app.
const { useState: useStateApp } = React;

function App() {
  const [active, setActive] = useStateApp("structured");
  const PanelComp = PANELS[active];
  return (
    <React.Fragment>
      <TopAppBar />
      <div className="shell">
        <NavDrawer active={active} onSelect={setActive} />
        <main className="main">
          <div className="main-head">
            <div className="prompt-line">spring-ai@demo:<span className="path">~/features</span>$ ./dev-console<span className="caret"></span></div>
            <h1>Developer-Konsole</h1>
            <p>Triggert die Backend-Endpunkte der Spring-AI-Features direkt aus dem Browser. Wähle links ein Feature.</p>
          </div>
          <PanelComp key={active} />
        </main>
      </div>
    </React.Fragment>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
