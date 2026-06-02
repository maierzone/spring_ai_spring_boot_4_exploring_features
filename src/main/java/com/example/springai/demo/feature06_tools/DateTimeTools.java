package com.example.springai.demo.feature06_tools;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Werkzeug-Sammlung für die Tool-Calling-Demo.
 *
 * <p>Jede mit {@link Tool} annotierte Methode wird dem Modell als aufrufbare
 * Funktion angeboten. Das Modell entscheidet selbst, ob und mit welchen
 * Argumenten es ein Tool aufruft; Spring AI führt den Java-Aufruf aus und reicht
 * das Ergebnis zurück ins Modell. Die {@code description} ist wichtig – daran
 * erkennt das Modell, wofür ein Tool gedacht ist.</p>
 */
public class DateTimeTools {

    /**
     * Liefert das aktuelle Datum samt Uhrzeit. Nützlich, weil das Modell selbst
     * kein Zeitgefühl hat und ohne Tool nur raten könnte.
     */
    @Tool(description = "Liefert das aktuelle Datum und die aktuelle Uhrzeit (lokale Serverzeit).")
    public String currentDateTime() {
        return LocalDateTime.now().toString();
    }

    /**
     * Beispiel für ein Tool mit Parameter: gibt den Wochentag zu einem Datum
     * zurück. {@link ToolParam} dokumentiert das Argument für das Modell.
     */
    @Tool(description = "Gibt den Wochentag fuer ein Datum im Format JJJJ-MM-TT zurueck.")
    public String dayOfWeek(@ToolParam(description = "Datum im ISO-Format JJJJ-MM-TT") String isoDate) {
        return LocalDateTime.parse(isoDate + "T00:00:00")
                .getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.GERMAN);
    }
}
