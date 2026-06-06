package com.example.springai.demo.feature15_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * Verifiziert den Laufzeit-Tracer des Gate-Deciders offline: Der {@link CallFlowAspect}
 * muss waehrend einer aktiven Aufzeichnung die tatsaechlich durchlaufenen Aufrufe als
 * verschachtelten {@link CallNode}-Baum festhalten – denselben Pfad, den man auch im
 * Debugger durchschreiten wuerde. Bewusst ohne Spring-AI/Modell und ohne vollen
 * Container: zwei Hilfs-Beans werden mit {@link AspectJProxyFactory} direkt umwoben.
 */
class CallFlowAspectTest {

    /** Innere "Tiefe": wird ueber die (umwobene) Bean-Grenze aufgerufen und damit getract. */
    static class Inner {
        public String step(String value) {
            return "inner:" + value;
        }
    }

    /** Aeussere Bean: delegiert an die innere Bean – erzeugt einen zweistufigen Aufruf. */
    static class Outer {
        private final Inner inner;

        Outer(Inner inner) {
            this.inner = inner;
        }

        public String work() {
            return inner.step("x");
        }
    }

    /** Umwebt ein Ziel mit dem Aspekt (CGLIB, da Klassen ohne Interface). */
    private static <T> T weave(T target, CallFlowAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    void zeichnetVerschachteltenAufrufBaumAuf() {
        CallFlowRecorder recorder = new CallFlowRecorder();
        CallFlowAspect aspect = new CallFlowAspect(recorder, new SqlTraceRecorder());

        Inner inner = weave(new Inner(), aspect);
        Outer outer = weave(new Outer(inner), aspect);

        recorder.start("Test.root()", "Test.java:1");
        outer.work();
        List<CallNode> flow = recorder.stopAndCollect();

        assertThat(flow).hasSize(1);
        CallNode root = flow.get(0);
        assertThat(root.method).isEqualTo("Test.root()");
        assertThat(root.calls).hasSize(1);

        CallNode work = root.calls.get(0);
        assertThat(work.method).isEqualTo("Outer.work()");
        assertThat(work.location).isNotNull(); // Aufruf kam aus eigenem (Test-)Code
        assertThat(work.calls).extracting(c -> c.method).containsExactly("Inner.step(String)");
    }

    @Test
    void ohneStartWirdNichtsAufgezeichnet() {
        CallFlowRecorder recorder = new CallFlowRecorder();
        CallFlowAspect aspect = new CallFlowAspect(recorder, new SqlTraceRecorder());
        Outer outer = weave(new Outer(weave(new Inner(), aspect)), aspect);

        outer.work(); // keine Aufzeichnung aktiv -> Aspekt reicht nur durch

        assertThat(recorder.isRecording()).isFalse();
        assertThat(recorder.stopAndCollect()).isEmpty();
    }
}
