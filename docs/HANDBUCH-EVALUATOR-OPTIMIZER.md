# Feature 16 – Evaluator-Optimizer (selbstkorrigierende KI)

Vertieftes Handbuch zu **Feature 16**. Schreibstil wie `HANDBUCH.md`:
kurzes Konzept, dann Code, Konfiguration, `curl`-Beispiel und die Fallstricke,
die in der Praxis weh tun.

| # | Feature | Paket | Endpunkt |
|---|---------|-------|----------|
| 16 | **Evaluator-Optimizer** – LLM verbessert seine eigene Ausgabe in einer Schleife | `feature16_evaluator` | `GET /api/evaluator/sql?question=…` |

> **Versionshinweis:** Code läuft auf **Spring Boot 4.0.6** mit **Spring AI
> 2.0.0-M8**. Das Evaluator-Optimizer-Muster ist eines der „Agentic Patterns",
> die mit der Spring-AI-2.0-Linie in den Vordergrund rücken – hier bewusst als
> **isoliertes, nachvollziehbares** Feature umgesetzt, nicht als großes
> Agenten-Framework.

---

## Worum geht es?

Feature 14 („Frag deine Datenbank") lässt das Modell aus einer Frage SQL erzeugen
und reicht es an den `SqlGuard` weiter. Das Problem: Der **erste Wurf** eines LLM
ist nicht zuverlässig korrekt. Das Modell rät Spaltennamen (`alter` statt
Berechnung aus `geburtsdatum`), missversteht das Schema oder vergisst eine
Bedingung.

Das **Evaluator-Optimizer-Pattern** löst das mit einer Kritik-Schleife:

1. **Generator** erzeugt einen Vorschlag (SQL).
2. **Evaluator** bewertet ihn gegen das Schema.
3. Ist er nicht gut genug, fließt die **Begründung als Feedback** in einen neuen
   Generierungs-Versuch.
4. Wiederholen, bis der Evaluator zustimmt **oder** die Iterationsgrenze greift.

Das ist der Übergang von „LLM rät einmal" zu „KI-System verbessert sich selbst,
bis ein Qualitätsmaßstab erfüllt ist".

```
Frage ("Wie viele Versicherte über 65?")
        │
        ▼
  [Generator-LLM]  ──►  SQL v1:  ... WHERE alter > 65
        │
        ▼
  [Evaluator]  ──►  { valid:false, feedback:"Spalte 'alter' existiert nicht,
        │                          nutze geburtsdatum" }
        │  (nicht ok → Feedback in den nächsten Versuch)
        ▼
  [Generator-LLM]  ──►  SQL v2:  ... WHERE geburtsdatum < now() - interval '65 years'
        │
        ▼
  [Evaluator]  ──►  { valid:true }
        │
        ▼
     ✅ akzeptiert (fließt weiter in den SqlGuard/die Ausführung aus Feature 14)
```

---

## Der Kern: eine reine, testbare Schleife

Die eigentliche Konvergenz-Logik steckt **Spring- und LLM-frei** in
`SqlEvaluatorOptimizer` – analog zum `SqlGuard` aus Feature 14. Generator und
Evaluator sind funktionale Schnittstellen; dadurch lässt sich das Verhalten
(Abbruch bei Erfolg, Feedback-Weitergabe, harte Iterationsgrenze) vollständig
**offline und deterministisch** testen.

```java
public static Outcome run(int maxIterations, Generator generator, Evaluator evaluator) {
    if (maxIterations < 1) {
        throw new IllegalArgumentException("maxIterations muss >= 1 sein.");
    }
    List<Attempt> attempts = new ArrayList<>();
    String sql = null;
    String feedback = null;

    for (int iteration = 1; iteration <= maxIterations; iteration++) {
        sql = generator.generate(sql, feedback);
        Evaluation evaluation = evaluator.evaluate(sql);
        attempts.add(new Attempt(iteration, sql, evaluation));

        if (evaluation.valid()) {
            return new Outcome(true, sql, List.copyOf(attempts));
        }
        feedback = evaluation.feedback();   // Kritik wird Kontext des nächsten Versuchs
    }
    return new Outcome(false, sql, List.copyOf(attempts));
}
```

Das `Outcome` enthält die **vollständige Versuchshistorie** (`attempts`) – die
„Beweiskette" der Verbesserung. Genau das macht das Feature in einer Demo
sichtbar: Man sieht v1 scheitern und v2 grün werden.

---

## Die Verdrahtung: zwei Rollen, ein Modell

Der Controller baut aus **einem** `ChatClient.Builder` zwei Clients mit
unterschiedlichem System-Prompt. `mutate()` leitet den zweiten aus dem ersten ab:

```java
this.generatorClient = builder.defaultSystem(GENERATOR_SYSTEM).build();
this.evaluatorClient = generatorClient.mutate().defaultSystem(EVALUATOR_SYSTEM).build();
```

### Geschichtete Bewertung (wiederverwendet Feature 14 + 4)

Der Evaluator ist **zweistufig** – billig vor teuer:

```java
private SqlEvaluatorOptimizer.Evaluator evaluator() {
    return sql -> {
        try {
            SqlGuard.sanitize(sql);                 // 1) deterministisch & gratis (Feature 14)
        } catch (IllegalArgumentException e) {
            return new Evaluation(false, "Sicherheits-Check fehlgeschlagen: " + e.getMessage());
        }
        return evaluatorClient.prompt()             // 2) LLM-Richter, nur wenn Guard durchlässt
                .user(u -> u.text("Bewerte dieses SQL:\n{sql}").param("sql", sql))
                .call()
                .entity(Evaluation.class);          // Structured Output (Feature 4)
    };
}
```

- **Stufe 1 – `SqlGuard`:** rein lesend? nur ein Statement? keine Kommentare?
  Schlägt der Guard an, sparen wir den Modellaufruf – seine Fehlermeldung wird
  direkt zum Feedback.
- **Stufe 2 – LLM-Richter:** prüft die **semantische** Korrektheit gegen das
  Schema und liefert sein Urteil als typisierten `Evaluation`-Record
  (`.entity(...)`), kein String-Parsing.

Schema wird **beiden** Prompts mitgegeben – sonst halluziniert der Richter selbst
über die Tabellenstruktur.

### Bezug zu Feature 13

Feature 13 setzt einen Evaluator als **Mess-Werkzeug am Ende** ein (Pass/Fail
nach der Antwort). Hier wird Evaluation zum **aktiven Regelkreis mitten im Flow**
– derselbe Baustein, andere Rolle.

---

## Ausprobieren (curl)

Server unter `http://localhost:8080`, gültiger `ANTHROPIC_API_KEY` vorausgesetzt
(zwei bis sechs Modellaufrufe je Anfrage – siehe Fallstricke).

```bash
# Klassischer Stolperstein: Alter aus geburtsdatum statt einer Spalte 'alter'
curl "localhost:8080/api/evaluator/sql?question=Wie+viele+Versicherte+sind+aelter+als+65+Jahre?"
```

Antwort (`Outcome` als JSON, gekürzt):

```json
{
  "success": true,
  "sql": "SELECT count(*) FROM versicherte WHERE geburtsdatum < now() - interval '65 years'",
  "attempts": [
    { "iteration": 1, "sql": "... WHERE alter > 65",
      "evaluation": { "valid": false, "feedback": "Spalte 'alter' existiert nicht ..." } },
    { "iteration": 2, "sql": "... WHERE geburtsdatum < now() - interval '65 years'",
      "evaluation": { "valid": true, "feedback": "..." } }
  ]
}
```

Die `attempts`-Liste ist der eigentliche Mehrwert für die Demo: der Vorher/Nachher-
Kontrast wird ohne Erklärung sichtbar.

---

## Tests (offline, ohne API-Key)

`SqlEvaluatorOptimizerTest` deckt die drei Verhaltensweisen ab, auf die es ankommt
– mit Lambdas statt echtem Modell:

- **Sofort-Erfolg:** erster Versuch akzeptiert → genau ein `Attempt`.
- **Verbesserung nach Feedback:** erster Wurf falsch, nach Feedback korrekt →
  zwei `Attempt`s, Konvergenz.
- **Harter Abbruch:** Evaluator sagt immer `false` → `success=false`, exakt
  `maxIterations` Versuche.

Zusätzlich wird das **Code-Fence-Stripping** geprüft (Modelle verpacken SQL trotz
Anweisung oft in ```` ```sql ... ``` ````, was der `SqlGuard` sonst ablehnt).

---

## Fallstricke (das, was in der Praxis weh tut)

1. **Kosten & Latenz multiplizieren sich.** Jede Iteration = Generator- **plus**
   Evaluator-Aufruf. Bei `maxIterations=3` sind das bis zu 6 Modellaufrufe für
   *eine* Antwort. → Mit Feature 12 (Observability) den Token-Verbrauch sichtbar
   machen; den deterministischen `SqlGuard`-Vorfilter bewusst *vor* den LLM-Richter
   setzen, um im Fehlerfall Aufrufe zu sparen.
2. **Endlosschleifen-Schutz ist Pflicht.** Ohne harte `maxIterations`-Grenze kann
   die Schleife bei nie konvergierendem Modell „ewig" laufen. Hier garantiert die
   `for`-Schleife die Terminierung; `success=false` ist ein legitimes Ergebnis.
3. **Wer evaluiert den Evaluator?** Der Richter kann selbst irren (falsch-positiv:
   akzeptiert fehlerhaftes SQL; falsch-negativ: verwirft korrektes). Das Muster
   verschiebt das Risiko, es eliminiert es nicht. Der deterministische `SqlGuard`
   fängt zumindest die *gefährlichen* Fehler hart ab.
4. **Nicht-Determinismus in der Live-Demo.** Gleiche Frage → evtl. andere
   SQL-Fassungen/Iterationszahl. Für eine verlässliche Vorführung eine Frage
   wählen, die den ersten Versuch reproduzierbar scheitern lässt (Alter aus
   `geburtsdatum`).
5. **Markdown-Wrapping.** Modelle setzen trotz „nur nacktes SQL"-Anweisung gern
   Code-Blöcke. `stripCodeFences(...)` entfernt sie, bevor der `SqlGuard` greift.
