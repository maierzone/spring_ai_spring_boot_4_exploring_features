package com.example.springai.demo.feature15_gateway;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * FEATURE 15 – Laufzeit-Tracer fuer den Methoden-Aufruf-Flow des Gate-Deciders.
 *
 * <p>Ein {@code @Around}-Aspekt ueber allen Methoden unter
 * {@code com.example.springai.demo.*}: Solange der {@link CallFlowRecorder} auf dem
 * aktuellen Thread aufzeichnet (also waehrend einer {@code /api/gateway}-Anfrage),
 * meldet jeder Aufruf seinen Ein- und Austritt. So entsteht <b>derselbe</b>
 * Aufrufpfad, den man auch beim Schritt-fuer-Schritt-Debugging sehen wuerde – nur
 * automatisch als Baum eingesammelt statt von Hand durchgeklickt.</p>
 *
 * <p><b>Grenze (bewusst):</b> Spring-AOP wirkt nur auf Bean-zu-Bean-Aufrufe ueber den
 * Proxy. Objekt-interne {@code this.helfer()}-Aufrufe und reine Static-Util-Klassen
 * (z.&nbsp;B. {@code SqlGuard}) erscheinen daher nicht – genau wie ein an Methoden-
 * Breakpoints haengender Debugger ueberspringt der Tracer keine Frames, sieht aber
 * nur die Aufrufe, die tatsaechlich ueber eine Bean-Grenze laufen.</p>
 */
@Aspect
@Component
public class CallFlowAspect {

    private static final String BASE = "com.example.springai.demo";

    private final CallFlowRecorder recorder;

    public CallFlowAspect(CallFlowRecorder recorder) {
        this.recorder = recorder;
    }

    @Around("execution(* com.example.springai.demo..*(..)) "
            + "&& !within(com.example.springai.demo.feature15_gateway.CallFlowAspect) "
            + "&& !within(com.example.springai.demo.feature15_gateway.CallFlowRecorder)")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        if (!recorder.isRecording()) {
            return pjp.proceed();
        }
        recorder.enter(label(pjp), callerLocation());
        try {
            return pjp.proceed();
        } finally {
            recorder.exit();
        }
    }

    /** Formatiert die aufgerufene Methode als {@code Klasse.methode(Param, …)}. */
    private static String label(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String params = Arrays.stream(sig.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return sig.getDeclaringType().getSimpleName() + "." + sig.getName() + "(" + params + ")";
    }

    /**
     * Beste Naeherung der Quell-Stelle, von der der Aufruf ausging – das Pendant zum
     * aufrufenden Frame im Debugger. Wir ueberspringen die Aspekt- und AOP-/Reflection-
     * Frames; das erste verbleibende Frame liefert nur dann eine Zeile, wenn der Aufruf
     * aus eigenem Code kam (sonst {@code null}, z.&nbsp;B. beim Spring-AI-Tool-Calling).
     */
    private String callerLocation() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean passedAspect = false;
        for (StackTraceElement frame : stack) {
            String cn = frame.getClassName();
            if (cn.equals(getClass().getName())) {
                passedAspect = true;
                continue;
            }
            if (!passedAspect) {
                continue;
            }
            if (cn.startsWith("org.springframework.aop") || cn.startsWith("org.springframework.cglib")
                    || cn.startsWith("jdk.internal.reflect") || cn.startsWith("java.lang.reflect")
                    || cn.contains("SpringCGLIB")) {
                continue;
            }
            if (cn.startsWith(BASE)) {
                String file = frame.getFileName();
                return (file != null ? file : cn) + ":" + frame.getLineNumber();
            }
            return null;
        }
        return null;
    }
}
