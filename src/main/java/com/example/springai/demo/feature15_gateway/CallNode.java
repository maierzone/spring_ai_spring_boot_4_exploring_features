package com.example.springai.demo.feature15_gateway;

import java.util.ArrayList;
import java.util.List;

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
