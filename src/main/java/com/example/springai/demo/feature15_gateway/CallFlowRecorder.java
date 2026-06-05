package com.example.springai.demo.feature15_gateway;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * FEATURE 15 – Aufzeichnung des echten Methoden-Aufruf-Flows des Gate-Deciders.
 *
 * <p>Waehrend einer {@code /api/gateway}-Anfrage schneidet der {@link CallFlowAspect}
 * die tatsaechlich durchlaufenen Methodenaufrufe mit und baut hier einen Aufruf-Baum
 * auf – denselben Weg, den man auch im Debugger durchschreiten wuerde, nur als Daten
 * statt als Breakpoints. Der {@link GatewayController} startet die Aufzeichnung vor dem
 * Modellaufruf und liest den Baum danach aus.</p>
 *
 * <p>Bewusst <b>thread-lokal</b> statt {@code @RequestScope}: Tool-Calling laeuft
 * synchron auf demselben Anfrage-Thread, und Hintergrund-Threads (z.&nbsp;B. der
 * Docs-RAG-Worker aus Feature 18) sehen so schlicht keine aktive Aufzeichnung –
 * ohne an einem fehlenden Request-Scope zu scheitern.</p>
 */
@Component
public class CallFlowRecorder {

    /** Aufzeichnungs-Zustand eines einzelnen Threads: Wurzel + offener Aufruf-Stack. */
    private static final class Recording {
        final CallNode root;
        final Deque<CallNode> stack = new ArrayDeque<>();

        Recording(CallNode root) {
            this.root = root;
            this.stack.push(root);
        }
    }

    private final ThreadLocal<Recording> current = new ThreadLocal<>();

    /** Beginnt die Aufzeichnung mit dem Einstiegspunkt als Wurzel des Baums. */
    public void start(String rootMethod, String location) {
        current.set(new Recording(new CallNode(rootMethod, location)));
    }

    /** {@code true}, solange auf diesem Thread aufgezeichnet wird. */
    public boolean isRecording() {
        return current.get() != null;
    }

    /** Betritt eine Methode: haengt sie unter den aktuell offenen Aufruf und merkt sie als neuen Kontext. */
    public void enter(String method, String location) {
        Recording r = current.get();
        if (r == null) {
            return;
        }
        CallNode node = new CallNode(method, location);
        r.stack.peek().calls.add(node);
        r.stack.push(node);
    }

    /** Verlaesst die zuletzt betretene Methode (die Wurzel bleibt immer erhalten). */
    public void exit() {
        Recording r = current.get();
        if (r != null && r.stack.size() > 1) {
            r.stack.pop();
        }
    }

    /** Beendet die Aufzeichnung und liefert den Aufruf-Baum (Wurzel als einziges Element). */
    public List<CallNode> stopAndCollect() {
        Recording r = current.get();
        current.remove();
        return r == null ? List.of() : List.of(r.root);
    }
}
