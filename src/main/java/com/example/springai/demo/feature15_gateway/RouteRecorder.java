package com.example.springai.demo.feature15_gateway;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

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
