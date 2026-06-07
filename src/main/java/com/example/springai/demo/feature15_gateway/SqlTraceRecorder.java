package com.example.springai.demo.feature15_gateway;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

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
