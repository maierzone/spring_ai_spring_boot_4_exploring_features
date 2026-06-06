package com.example.springai.demo.feature15_gateway;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * FEATURE 15 – Mitschnitt der rohen DB-Tool-Ergebnisse einer Gateway-Anfrage.
 *
 * <p>Wenn der Gate-Decider auf die Datenbank-Route faellt, ruft das Modell ein oder
 * mehrere Datenbank-Tools auf ({@code EgkQueryTools}, {@code ReadOnlySqlTool}). Jedes
 * davon liefert seinen <b>rohen</b> Rueckgabewert – die Zahlen/Zeilen, bevor das Modell
 * daraus Fliesstext formuliert. Dieser Recorder haelt genau diese Roh-Ausgaben fest, eine
 * pro Tool-Aufruf, damit die Konsole sie auf der Rueckseite der umklappbaren Antwort-Karte
 * zeigen kann.</p>
 *
 * <p>Wie {@link CallFlowRecorder} bewusst <b>thread-lokal</b>: Tool-Calling laeuft synchron
 * auf dem Anfrage-Thread; der {@link CallFlowAspect} schreibt waehrend der Aufzeichnung hier
 * hinein, der {@link GatewayController} liest danach aus.</p>
 */
@Component
public class SqlTraceRecorder {

    /** Ein roher Tool-Rueckgabewert: welches Tool, und was es zurueckgab. */
    public record Entry(String tool, String raw) {
    }

    private final ThreadLocal<List<Entry>> current = new ThreadLocal<>();

    /** Beginnt die Aufzeichnung fuer den aktuellen Thread. */
    public void start() {
        current.set(new ArrayList<>());
    }

    /** {@code true}, solange auf diesem Thread aufgezeichnet wird. */
    public boolean isRecording() {
        return current.get() != null;
    }

    /** Haelt den rohen Rueckgabewert eines DB-Tool-Aufrufs fest. */
    public void record(String tool, String raw) {
        List<Entry> entries = current.get();
        if (entries != null) {
            entries.add(new Entry(tool, raw));
        }
    }

    /** Beendet die Aufzeichnung und liefert die gesammelten Roh-Ausgaben. */
    public List<Entry> stopAndCollect() {
        List<Entry> entries = current.get();
        current.remove();
        return entries == null ? List.of() : entries;
    }
}
