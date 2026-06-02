Spring AI ist ein Framework, das die Integration von KI-Modellen (Chat, Embeddings, Bildgenerierung) in Spring-Anwendungen vereinfacht und dabei bewusst dem bekannten Spring-Programmiermodell folgt.

Der ChatClient ist die zentrale, fluent API von Spring AI. Mit ihr baut man einen Prompt zusammen (System- und User-Nachricht), ruft das Modell auf und liest die Antwort aus. Der ChatClient ist providerunabhaengig.

Structured Output bezeichnet die Faehigkeit, eine Modellantwort direkt in ein typisiertes Java-Objekt zu ueberfuehren. Spring AI generiert dazu aus der Zielklasse ein JSON-Schema, gibt es dem Modell als Formatanweisung mit und deserialisiert die Antwort.

Tool Calling (auch Function Calling) erlaubt es dem Modell, waehrend der Antwortgenerierung annotierte Java-Methoden aufzurufen. So gelangt das Modell an aktuelle Daten aus Datenbanken oder externen APIs, statt zu raten.

RAG steht fuer Retrieval Augmented Generation. Dabei werden zur Frage passende Dokumente aus einem Vektorspeicher gesucht und als Kontext an den Prompt angehaengt. Das reduziert Halluzinationen und haelt Antworten aktuell.

Ein Embedding ist die Uebersetzung eines Textes in einen Zahlenvektor. Texte mit aehnlicher Bedeutung liegen im Vektorraum nahe beieinander. Embeddings sind die Grundlage fuer semantische Suche und RAG.

Advisors sind das Erweiterungskonzept von Spring AI. Sie klinken sich in den Anfrage- und Antwortfluss ein, aehnlich wie Servlet-Filter. Chat-Memory und RAG sind selbst als Advisors umgesetzt.

Chat Memory loest das Problem, dass ein LLM von Natur aus zustandslos ist. Ein Memory-Advisor laedt den bisherigen Gespraechsverlauf je Konversations-ID und haengt ihn an den Prompt an.
