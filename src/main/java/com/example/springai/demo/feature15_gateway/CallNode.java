package com.example.springai.demo.feature15_gateway;

import java.util.ArrayList;
import java.util.List;

/**
 * FEATURE 15 – Ein Knoten im aufgezeichneten Methoden-Aufruf-Flow des Gate-Deciders.
 *
 * <p>Bewusst ein simpler, baumartiger Datenhalter (keine Logik): {@link #method} ist
 * die aufgerufene Methode ({@code Klasse.methode(Param, …)}), {@link #location} die
 * beste Naeherung der Quell-Stelle ({@code Datei.java:Zeile}) – analog zu dem, was
 * ein Debugger im Call-Stack-Frame zeigt – und {@link #calls} die von hier aus
 * getaetigten weiteren Aufrufe. Die oeffentlichen Felder werden direkt zu JSON
 * serialisiert und vom UI als sichtbare Aufruf-Kette gerendert.</p>
 */
public final class CallNode {

    /** Aufgerufene Methode, z.&nbsp;B. {@code GatewayTools.datenbank(String)}. */
    public final String method;

    /** Quell-Stelle des Aufrufs ({@code Datei.java:Zeile}) oder {@code null}, wenn der
     *  Aufruf aus dem Framework kam (z.&nbsp;B. Spring-AI-Tool-Calling). */
    public final String location;

    /** Von dieser Methode aus getaetigte (getracte) Folgeaufrufe – in Aufrufreihenfolge. */
    public final List<CallNode> calls = new ArrayList<>();

    CallNode(String method, String location) {
        this.method = method;
        this.location = location;
    }
}
