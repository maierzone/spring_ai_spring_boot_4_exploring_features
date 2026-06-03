package com.example.springai.demo.feature15_gateway;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * FEATURE 15 – Routing-Protokoll des Gate-Deciders (pro HTTP-Anfrage).
 *
 * <p>Beim Tool-Calling waehlt das Modell selbst, welches Feature-Tool es aufruft,
 * und formuliert anschliessend frei eine Schlussantwort. Fuer eine nachvollziehbare
 * Router-Demo wollen wir aber die <b>roh durchgereichte</b> Antwort des gewaehlten
 * Controllers plus die getroffene Route – nicht die Neuformulierung des Modells.
 * Dieser request-scoped Recorder haelt genau das fest: Das jeweils ausgefuehrte
 * Gate-Tool ({@link GatewayTools}) schreibt Route und Ergebnis hier hinein, der
 * {@link GatewayController} liest es nach dem Aufruf wieder aus.</p>
 *
 * <p>Bewusst {@code @RequestScope}, damit parallele Gateway-Anfragen sich nicht
 * gegenseitig ueberschreiben (jede Anfrage bekommt ihre eigene Instanz).</p>
 */
@Component
@RequestScope
public class RouteRecorder {

    private String route;
    private String antwort;

    /** Haelt die vom gewaehlten Gate-Tool getroffene Entscheidung fest. */
    public void record(String route, String antwort) {
        this.route = route;
        this.antwort = antwort;
    }

    /** {@code true}, sobald ein Gate-Tool eine Route gewaehlt hat. */
    public boolean isResolved() {
        return route != null;
    }

    public String route() {
        return route;
    }

    public String antwort() {
        return antwort;
    }
}
