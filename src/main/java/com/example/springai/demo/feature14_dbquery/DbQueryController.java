package com.example.springai.demo.feature14_dbquery;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 14 – "Frag deine Datenbank" (natuerlichsprachliche DB-Abfrage).
 *
 * <p>Beantwortet Fragen zum eGK-Datenbestand, indem das Modell die Datenbank die
 * Schwerarbeit machen laesst: Es waehlt bevorzugt ein kuratiertes, geprueftes
 * Aggregations-Tool ({@link EgkQueryTools}); passt keines, formuliert es selbst
 * ein abgesichertes {@code SELECT} ({@link ReadOnlySqlTool}). Postgres zaehlt und
 * gruppiert ueber Indizes – das skaliert auf Millionen Zeilen und liefert exakte
 * Zahlen, waehrend das Modell nur das kleine Ergebnis in eine Antwort giesst.</p>
 *
 * <p>Bewusst <b>keine</b> Vektorsuche/Embeddings: Aggregations-Fragen ("wie viele",
 * "Verteilung") brauchen exakte Zaehlung, kein Aehnlichkeitsraten.</p>
 *
 * <p>Beispiel: {@code GET /api/db/ask?question=Wie viele Versicherte haben E11.9?}</p>
 */
@RestController
public class DbQueryController {

    /**
     * Schema-Beschreibung im System-Prompt: gibt dem Modell den noetigen Kontext,
     * um das passende Tool zu waehlen bzw. (im Fallback) korrektes SQL zu schreiben.
     */
    private static final String SYSTEM_PROMPT = """
            Du beantwortest Fragen zu einer synthetischen eGK-/Telematik-Datenbank (PostgreSQL).
            Nutze IMMER die bereitgestellten Tools, um an Zahlen zu kommen – erfinde niemals Werte.
            Bevorzuge die spezialisierten Abfrage-Tools. Nur wenn keines passt, schreibe ein
            einzelnes, rein lesendes SELECT und rufe das SQL-Tool auf.
            Antworte knapp auf Deutsch und nenne die konkreten Zahlen aus den Tool-Ergebnissen.

            Schema (Tabellen und wichtige Spalten):
              krankenkassen(id, ik, name, typ[GKV/PKV])
              leistungserbringer(id, typ[ARZT/APOTHEKE/KRANKENHAUS], name, lanr, bsnr, plz, ort)
              versicherte(id, kvnr, vorname, nachname, geburtsdatum, geschlecht[M/W], plz, ort,
                          strasse, krankenkasse_id -> krankenkassen.id)
              egk_karten(id, iccsn, can, versicherte_id -> versicherte.id, gueltig_von, gueltig_bis,
                         status[AKTIV/GESPERRT/ERSETZT])
              diagnosen(id, versicherte_id -> versicherte.id, leistungserbringer_id, icd10,
                        diagnose_text, diagnose_datum)
              zertifikate(id, typ[HBA/SMC_B/EGK_AUT/EGK_ENC], seriennummer, subject_dn,
                          ca_id -> ca_zertifikate.id, inhaber_typ, inhaber_id, not_before, not_after,
                          status[VALID/EXPIRED/REVOKED], revocation_datum)
            """;

    private final ChatClient chatClient;
    private final EgkQueryTools queryTools;
    private final ReadOnlySqlTool sqlTool;

    public DbQueryController(ChatClient.Builder builder, EgkQueryTools queryTools,
                             ReadOnlySqlTool sqlTool) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.queryTools = queryTools;
        this.sqlTool = sqlTool;
    }

    @GetMapping("/api/db/ask")
    public String ask(
            @RequestParam(defaultValue = "Wie viele Versicherte gibt es?") String question) {
        return chatClient.prompt()
                .user(question)
                // Beide Tool-Saetze anbieten; das Modell waehlt das kuratierte Tool
                // oder faellt auf das abgesicherte SQL-Tool zurueck.
                .tools(queryTools, sqlTool)
                .call()
                .content();
    }
}
