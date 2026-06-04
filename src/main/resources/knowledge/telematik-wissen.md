Die Telematikinfrastruktur (TI) ist das geschlossene, abgesicherte Netz des deutschen Gesundheitswesens. Sie verbindet Praxen, Apotheken, Krankenhaeuser und Krankenkassen und stellt Anwendungen wie das E-Rezept und die elektronische Patientenakte bereit. Betrieben und zugelassen wird die TI durch die gematik.

Die elektronische Gesundheitskarte (eGK) ist der Versichertenausweis als Chipkarte. Sie traegt die Stammdaten des Versicherten und kryptografische Zertifikate, mit denen sich der Versicherte in der Telematikinfrastruktur ausweist. Jede eGK hat eine eindeutige Chipnummer (ICCSN) und eine Card Access Number (CAN).

Die Krankenversichertennummer (KVNR) identifiziert einen Versicherten lebenslang und bundesweit eindeutig. Sie besteht aus einem Buchstaben (A bis Z) gefolgt von acht Ziffern und einer abschliessenden Pruefziffer, also insgesamt zehn Stellen. Die Pruefziffer wird nach einem festen GKV-Verfahren aus den vorangehenden Stellen berechnet.

Das Institutionskennzeichen (IK) ist eine neunstellige Nummer, die Krankenkassen und andere Einrichtungen im Gesundheitswesen eindeutig kennzeichnet. Die letzte Stelle ist eine Luhn-Pruefziffer, mit der sich Tippfehler erkennen lassen.

Die Lebenslange Arztnummer (LANR) identifiziert einen einzelnen Arzt, die Betriebsstaettennummer (BSNR) die Arztpraxis als Betriebsstaette. Beide werden bei der vertragsaerztlichen Abrechnung verwendet und einem Leistungserbringer zugeordnet.

ICD-10 ist die internationale Klassifikation der Krankheiten in ihrer zehnten Revision. Jeder Diagnose wird ein alphanumerischer Code zugeordnet, zum Beispiel E11.9 fuer einen nicht naeher bezeichneten Typ-2-Diabetes oder I10 fuer essentielle Hypertonie (Bluthochdruck). Der Code macht Diagnosen maschinell auswertbar.

Eine eGK kann verschiedene Status haben. AKTIV bedeutet, die Karte ist gueltig und einsatzbereit. GESPERRT bedeutet, die Karte wurde gesperrt, etwa nach Verlust oder Diebstahl, und darf nicht mehr verwendet werden. ERSETZT bedeutet, die Karte wurde durch eine neue Karte abgeloest.

In der Telematikinfrastruktur gibt es mehrere Zertifikatstypen. Der Heilberufsausweis (HBA) weist eine einzelne Person wie einen Arzt persoenlich aus. Der Security Module Card Typ B (SMC-B) weist eine Institution wie eine Praxis oder Apotheke aus. Auf der eGK selbst liegen die Zertifikate EGK_AUT zur Authentisierung und EGK_ENC zur Verschluesselung.

Jedes Zertifikat ist nur innerhalb eines Gueltigkeitszeitraums verwendbar, der durch not_before und not_after begrenzt ist. Der Status eines Zertifikats ist VALID, wenn es gueltig ist, EXPIRED nach Ablauf des Zeitraums oder REVOKED, wenn es vorzeitig zurueckgezogen (gesperrt) wurde.

Zertifikate werden von einer Zertifizierungsstelle (Certificate Authority, CA) ausgestellt. Die Vertrauenskette (Trust-Chain) fuehrt vom Zertifikat eines Heilberuflers oder einer Karte ueber die ausstellende CA bis zu einer obersten Vertrauensinstanz. Nur Zertifikate mit lueckenloser, gueltiger Kette werden in der TI akzeptiert.

In Deutschland unterscheidet man die gesetzliche Krankenversicherung (GKV) und die private Krankenversicherung (PKV). Die GKV umfasst den groessten Teil der Bevoelkerung und finanziert sich ueber einkommensabhaengige Beitraege; die PKV richtet sich nach individuell vereinbarten Tarifen. Jeder Versicherte ist genau einer Krankenkasse zugeordnet.
