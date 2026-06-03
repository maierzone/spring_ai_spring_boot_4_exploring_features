# Troubleshooting: Anthropic `401 – invalid x-api-key`

> Gilt für dieses Projekt: **Spring Boot 4.0.6 · Spring AI 2.0.0-M8 ·
> `spring-ai-starter-model-anthropic`** (intern das offizielle
> `anthropic-java-core` 2.34.0, Auth-Header `x-api-key`).

Du bekommst beim ersten **echten** Modellaufruf (z. B. `GET /api/chat?message=…`)
einen HTTP 401. Der Roh-Stacktrace dazu liegt unter
[`INVALID_API_KEY_ANTHRIPIC_ERROR_401.txt`](./INVALID_API_KEY_ANTHRIPIC_ERROR_401.txt):

```
com.anthropic.errors.UnauthorizedException: 401:
{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"},
 "request_id":"req_011Cbfes..."}
```

Diese Datei grenzt die Ursache systematisch ein und zeigt die Abhilfe.

---

## TL;DR – 30-Sekunden-Diagnose

Führe diesen einen Test **in genau der Shell aus, aus der du auch
`mvn spring-boot:run` startest**:

```bash
# 1) Kommt überhaupt ein (vollständiger) Key in der Umgebung an?
printf 'len=%s  prefix=%s…\n' "$(printf '%s' "$ANTHROPIC_API_KEY" | wc -c)" "$(printf '%s' "$ANTHROPIC_API_KEY" | head -c 14)"

# 2) Akzeptiert Anthropic diesen Key direkt – ganz ohne Spring?
curl -s -o /dev/null -w "HTTP %{http_code}\n" https://api.anthropic.com/v1/messages \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -H "content-type: application/json" \
  -d '{"model":"claude-haiku-4-5-20251001","max_tokens":16,"messages":[{"role":"user","content":"ping"}]}'
```

Auswertung:

| Beobachtung | Bedeutung | Weiter bei |
|---|---|---|
| `len=0` oder `prefix=…` leer | Env-Var ist in **dieser** Shell nicht gesetzt | [Ursache 1](#ursache-1--env-var-fehlt--platzhalter-greift-häufigster-fall) |
| `len` ≪ erwartet (z. B. < 50) | Key abgeschnitten / unvollständig kopiert | [Ursache 3](#ursache-3--key-abgeschnitten-vertippt-oder-mit-zeilenumbruchquotes) |
| `prefix=demo-key-not…` | Es greift der **Platzhalter** aus den Properties | [Ursache 1](#ursache-1--env-var-fehlt--platzhalter-greift-häufigster-fall) |
| curl **`HTTP 200`** | Key ist gültig → Problem liegt in der **Spring-Verdrahtung** | [Ursache 2](#ursache-2--key-gesetzt-aber-nicht-dort-wo-die-app-läuft) / [Ursache 6](#ursache-6--property-precedence-ein-anderer-wert-überschreibt-deinen) |
| curl **`HTTP 401`** | Key ist **wirklich** ungültig → Spring ist unschuldig | [Ursache 3](#ursache-3--key-abgeschnitten-vertippt-oder-mit-zeilenumbruchquotes)–[5](#ursache-5--falscher-key-typ-org-oder-workspace) |

> **Kernaussage:** Der curl-Test trennt sauber **„Key kaputt"** (curl 401) von
> **„App findet den Key nicht / nimmt den falschen"** (curl 200, App 401). Damit
> sparst du dir das Rätselraten.

---

## Was bedeutet dieser 401 genau?

Anthropic liefert in `error.type` den präzisen Grund. Wichtig: **401 ist immer ein
Authentifizierungs-Problem – nie ein Guthaben- oder Limit-Problem.**

| HTTP | `error.type` | Bedeutung | Das ist **nicht** dein Fall, wenn… |
|------|--------------|-----------|-------------------------------------|
| **401** | `authentication_error` | Key fehlt, ist falsch, widerrufen oder Tippfehler | → **genau das hast du** |
| 403 | `permission_error` | Key gültig, aber keine Berechtigung (z. B. falscher Workspace/Scope) | du bekommst 401, nicht 403 |
| 400 | `invalid_request_error` | Request falsch (z. B. unbekanntes Modell) | Auth wäre dann ok |
| 402 / `credit balance` | `billing` | Kein Guthaben / kein aktives Billing | das wäre kein 401 |
| 429 | `rate_limit_error` | Zu viele Anfragen | das wäre kein 401 |

`message: "invalid x-api-key"` heißt wörtlich: Der Wert im Header `x-api-key`
wurde von Anthropic **abgelehnt**. Entweder kommt der falsche Wert an, oder gar keiner.

---

## Wie der Key in diesem Projekt fließt

```
Shell-Env  ANTHROPIC_API_KEY
        │
        ▼
application.properties
  spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:demo-key-not-configured}
        │                                          └── Fallback, wenn Env-Var fehlt!
        ▼
AnthropicChatModel  ──HTTP──►  api.anthropic.com   (Header: x-api-key)
```

Siehe [`src/main/resources/application.properties`](../src/main/resources/application.properties):

```properties
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:demo-key-not-configured}
spring.ai.anthropic.chat.model=claude-haiku-4-5-20251001
```

**Die Falle:** Ist `ANTHROPIC_API_KEY` nicht gesetzt, **startet die App trotzdem**
(bewusst so, damit Tests/CI offline laufen) – aber jeder echte Aufruf geht mit dem
String `demo-key-not-configured` raus und kassiert garantiert einen 401. Es gibt
**keine** Warnung beim Start.

---

## Ursachen-Checkliste (häufigste zuerst)

### Ursache 1 — Env-Var fehlt → Platzhalter greift (häufigster Fall)

Die App läuft in einer anderen Shell/Prozessumgebung, in der
`ANTHROPIC_API_KEY` nicht (mehr) gesetzt ist.

**Prüfen:**
```bash
printenv ANTHROPIC_API_KEY        # leer = nicht gesetzt
```

**Abhilfe – Variable in derselben Shell setzen, dann starten:**
```bash
export ANTHROPIC_API_KEY="sk-ant-api03-…"   # echter Key
mvn spring-boot:run
```

> `export` gilt nur für die **aktuelle** Shell-Sitzung. Neues Terminal, `tmux`-Pane,
> `sudo`, ein Cron-Job oder ein anderer User → Variable ist wieder weg. Für Dauerhaftigkeit
> siehe [Key sicher & dauerhaft setzen](#key-sicher--dauerhaft-setzen).

---

### Ursache 2 — Key gesetzt, aber **nicht dort, wo die App läuft**

Klassiker: Du hast `export …` im Terminal gemacht, startest die App aber aus der
**IDE** (IntelliJ/VS Code/Eclipse). GUI-Anwendungen erben die Shell-Umgebung **nicht**
automatisch.

**Erkennungszeichen:** Der curl-Test oben gibt `HTTP 200`, die App aber 401.

**Abhilfe je nach IDE:** siehe [IDE-spezifisch](#ide-spezifisch).

---

### Ursache 3 — Key abgeschnitten, vertippt oder mit Zeilenumbruch/Quotes

Beim Kopieren (aus PDF, Slack, Passwortmanager) rutschen gern **unsichtbare Zeichen**
mit: ein abschließender Zeilenumbruch, Leerzeichen oder mitkopierte Anführungszeichen.
Anthropic-Keys haben das Format `sk-ant-api03-…` und sind **lang** (≈ 100+ Zeichen).

**Prüfen (entlarvt Länge + versteckte Zeichen):**
```bash
printf '%s' "$ANTHROPIC_API_KEY" | wc -c          # erwartet: ~100+, NICHT 0/klein
printf '%s' "$ANTHROPIC_API_KEY" | cat -A | tail   # $ am Zeilenende = mitkopierter Newline
```

**Abhilfe:**
```bash
# Falsch – Quotes werden Teil des Werts:
export ANTHROPIC_API_KEY="'sk-ant-…'"      # ❌ führende/abschließende ' im Wert
# Falsch – Backslash/Umbruch:
export ANTHROPIC_API_KEY="sk-ant-…
"                                          # ❌ Newline im Wert

# Richtig – sauber, ohne Zusatzzeichen:
export ANTHROPIC_API_KEY="sk-ant-api03-XXXXXXXX"   # ✅
```
Key am besten neu aus der Console kopieren (Doppelklick markiert manchmal zu wenig)
und direkt einfügen.

---

### Ursache 4 — Key widerrufen, gelöscht oder Tippfehler im Namen

- Der Key wurde in der [Anthropic Console](https://console.anthropic.com/settings/keys)
  **deaktiviert/rotiert/gelöscht** → alte Keys liefern sofort 401.
- Tippfehler im **Variablennamen** (`ANTHROPIC_API_KEY` ≠ `ANTROPIC_API_KEY`
  ≠ `ANTHROPIC_APIKEY`) → Env-Var bleibt faktisch leer → Platzhalter → 401.

**Abhilfe:** In der Console neuen Key erzeugen, Variablennamen exakt prüfen
(`ANTHROPIC_API_KEY`), neu setzen.

---

### Ursache 5 — Falscher Key-Typ, Org oder Workspace

- **Admin-Key statt API-Key:** `sk-ant-admin…`-Keys sind nur für die Admin-API
  (Org-Verwaltung) und werden vom Messages-Endpoint **abgelehnt**. Du brauchst einen
  normalen API-Key (`sk-ant-api03-…`).
- **Key eines anderen Anbieters:** ein OpenAI-Key (`sk-…` ohne `ant`) o. Ä. funktioniert
  hier nie.
- **Workspace/Org passt nicht:** Key aus einem Workspace, der gelöscht/deaktiviert ist.

**Abhilfe:** Sicherstellen, dass es ein **API-Key** aus dem **richtigen Workspace**
ist. Der curl-Test deckt das auf (er gibt dann ebenfalls 401/403).

---

### Ursache 6 — Property-Precedence: ein anderer Wert überschreibt deinen

Spring bezieht `spring.ai.anthropic.api-key` aus mehreren Quellen in fester
Reihenfolge. Ein „vergessener" Wert mit höherer Priorität kann deinen Env-Key
**aushebeln**. Reihenfolge (höher sticht):

1. Kommandozeile: `--spring.ai.anthropic.api-key=…`
2. `SPRING_AI_ANTHROPIC_API_KEY` (relaxed binding einer **anderen** Env-Var!)
3. `application-<profil>.properties` / `.yml`
4. `application.properties` → `${ANTHROPIC_API_KEY:demo-key-not-configured}`

**Prüfen** – was kommt im Code tatsächlich an? Property auflösen lassen, **ohne den
Key zu loggen** (nur Länge/Prefix):

```bash
# Stacktrace mit aufgelösten Properties:
mvn spring-boot:run -Ddebug

# Oder gezielt im Code/Endpoint (Beispiel):
#   @Value("${spring.ai.anthropic.api-key}") String key;
#   log.info("key len={} prefix={}", key.length(), key.substring(0, 8));
```

**Abhilfe:** Konkurrierende Quellen entfernen (kein hartcodierter Key in
`application*.properties`, kein versehentliches `SPRING_AI_ANTHROPIC_API_KEY`).

---

### Ursache 7 — Stale Build / alter Wert im Paket

Ein altes Fat-JAR oder gecachte `target/`-Klassen tragen evtl. noch eine alte
Konfiguration.

**Abhilfe:**
```bash
mvn clean spring-boot:run
# bzw. bei gebautem JAR:
mvn clean package && ANTHROPIC_API_KEY="sk-ant-…" java -jar target/spring-ai-demo-0.0.1-SNAPSHOT.jar
```

---

## Schritt-für-Schritt-Diagnose (Entscheidungsbaum)

```
401 invalid x-api-key
        │
        ▼
printenv ANTHROPIC_API_KEY  ── leer? ──► Ursache 1 (Env-Var setzen) ──► erneut testen
        │ gesetzt
        ▼
wc -c < 50 oder cat -A zeigt Müll? ──► Ursache 3 (sauber neu setzen)
        │ sieht ok aus
        ▼
curl-Direkttest gegen api.anthropic.com
        ├── HTTP 401/403 ──► Key selbst ungültig ► Ursachen 4 & 5 (neuer/richtiger Key)
        └── HTTP 200 ──────► Key ok, App nimmt ihn nicht
                                ├── Start aus IDE? ──► Ursache 2 (IDE-Env, s. u.)
                                └── sonst ──────────► Ursache 6 (Property-Precedence)
```

---

## Direkter API-Test (Key isoliert prüfen)

Der zuverlässigste Weg, **Key** von **App-Konfiguration** zu trennen:

```bash
curl https://api.anthropic.com/v1/messages \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -H "content-type: application/json" \
  -d '{
    "model": "claude-haiku-4-5-20251001",
    "max_tokens": 16,
    "messages": [{"role": "user", "content": "ping"}]
  }'
```

- **200 + JSON-Antwort** → Key ist gültig. Fehler liegt in der App/Umgebung
  (→ Ursache 2 oder 6).
- **401 `invalid x-api-key`** → Key ist tatsächlich kaputt/falsch
  (→ Ursache 3, 4 oder 5).

---

## Key sicher & dauerhaft setzen

**Niemals den Key in `application.properties`, ins Git oder in ein Slide schreiben.**
Die Properties referenzieren bewusst nur die Env-Var.

**Dauerhaft pro User (Login-Shell):**
```bash
echo 'export ANTHROPIC_API_KEY="sk-ant-api03-…"' >> ~/.bashrc   # bzw. ~/.zshrc
source ~/.bashrc
```

**Pro Projekt via `.env` (nicht committen!):** Eine `.env` anlegen und vor dem Start sourcen:
```bash
# .env  (in .gitignore aufnehmen)
ANTHROPIC_API_KEY=sk-ant-api03-…

# Starten:
set -a; source .env; set +a
mvn spring-boot:run
```
> Prüfe, dass `.env` in [`.gitignore`](../.gitignore) steht, bevor du committest.
> Wenn ein Key je versehentlich gepusht wurde: **sofort in der Console rotieren** –
> Entfernen aus der Historie allein reicht nicht.

---

## IDE-spezifisch

GUI-IDEs erben deine Terminal-Env nicht. Setze den Key in der Run-Konfiguration:

- **IntelliJ IDEA:** *Run/Debug Configurations → (deine Spring-Boot-Config) →
  Environment variables →* `ANTHROPIC_API_KEY=sk-ant-…`.
  (Alternativ das *EnvFile*-Plugin für `.env`.)
- **VS Code:** im `launch.json` unter `"env": { "ANTHROPIC_API_KEY": "sk-ant-…" }`,
  oder eine `.env` via `"envFile"`.
- **Eclipse/STS:** *Run Configurations → Environment → New →* `ANTHROPIC_API_KEY`.

Danach App **aus der IDE neu starten** (Variable wird nur beim Start gelesen).

---

## Schnellreferenz

| Symptom | Wahrscheinliche Ursache | Fix |
|---|---|---|
| `printenv ANTHROPIC_API_KEY` leer | nicht gesetzt | `export ANTHROPIC_API_KEY=…` in derselben Shell |
| Prefix `demo-key-not-configured` | Platzhalter greift | Env-Var setzen (Ursache 1) |
| `wc -c` viel zu klein | Key abgeschnitten | sauber neu kopieren (Ursache 3) |
| `cat -A` zeigt `$`/Leerzeichen | Newline/Whitespace im Wert | Wert ohne Zusatzzeichen setzen (Ursache 3) |
| curl 200, App 401 | App findet Key nicht | IDE-Env (Ursache 2) / Precedence (Ursache 6) |
| curl 401 | Key ungültig/widerrufen/falscher Typ | neuer API-Key aus richtigem Workspace (Ursache 4/5) |
| Terminal-Start ok, IDE-Start 401 | IDE erbt Env nicht | Run-Config-Env setzen |

---

## Was **nicht** die Ursache ist

- **Kein Guthaben/Billing:** Das wäre ein Billing-/402-Fehler, kein 401.
- **Rate Limit:** Das wäre ein 429, kein 401.
- **Falsches Modell** (`claude-haiku-4-5-20251001` nicht freigeschaltet): Das wäre ein
  400 `invalid_request_error` oder 404 – aber erst **nachdem** die Auth durch ist.
- **Docker/Postgres aus:** verhindert den App-**Start**, erzeugt aber keinen 401 vom Modell.

---

## Anhang

- Roh-Stacktrace: [`INVALID_API_KEY_ANTHRIPIC_ERROR_401.txt`](./INVALID_API_KEY_ANTHRIPIC_ERROR_401.txt)
- Key-Konfiguration: [`src/main/resources/application.properties`](../src/main/resources/application.properties)
- Start-Anleitung: [`README.md`](../README.md) → Abschnitt *Starten*
- Anthropic Console (Keys): <https://console.anthropic.com/settings/keys>
