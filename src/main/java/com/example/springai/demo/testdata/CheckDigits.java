package com.example.springai.demo.testdata;

/**
 * Pruefziffer-Algorithmen fuer fachlich plausible (aber synthetische) Nummern.
 *
 * <p>Macht die generierten Testdaten realistischer: Eine KVNR oder ein
 * Institutionskennzeichen besteht nicht aus Zufall, sondern traegt eine
 * Pruefziffer, die hier korrekt berechnet wird. Damit lassen sich die Daten
 * gegen echte Validierungslogik testen, ohne reale Personendaten zu verwenden.</p>
 */
public final class CheckDigits {

    private CheckDigits() {
    }

    /**
     * Berechnet die Pruefziffer der Krankenversichertennummer (KVNR) nach dem
     * Verfahren der GKV.
     *
     * <p>Die KVNR besteht aus 1 Buchstaben + 8 Ziffern + 1 Pruefziffer. Fuer die
     * Berechnung wird der Buchstabe durch seine zweistellige Position ersetzt
     * (A=01 ... Z=26), an die 8 Ziffern angehaengt (= 10 Ziffern) und ueber alle
     * Stellen alternierend mit 1 und 2 gewichtet; Produkte &gt; 9 werden um 9
     * reduziert (Quersumme). Pruefziffer = Summe modulo 10.</p>
     *
     * @param letter      Anfangsbuchstabe (A-Z)
     * @param achtZiffern die 8 Ziffern zwischen Buchstabe und Pruefziffer
     * @return die Pruefziffer (0-9)
     */
    public static int kvnrPruefziffer(char letter, String achtZiffern) {
        if (letter < 'A' || letter > 'Z') {
            throw new IllegalArgumentException("Buchstabe muss A-Z sein: " + letter);
        }
        if (achtZiffern.length() != 8 || !achtZiffern.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Erwartet genau 8 Ziffern: " + achtZiffern);
        }
        int position = letter - 'A' + 1;
        String basis = String.format("%02d", position) + achtZiffern; // 10 Ziffern
        int summe = 0;
        for (int i = 0; i < basis.length(); i++) {
            int ziffer = basis.charAt(i) - '0';
            int produkt = ziffer * (i % 2 == 0 ? 1 : 2); // erste Stelle Gewicht 1
            if (produkt > 9) {
                produkt -= 9;
            }
            summe += produkt;
        }
        return summe % 10;
    }

    /** Baut eine vollstaendige KVNR (Buchstabe + 8 Ziffern + Pruefziffer). */
    public static String kvnr(char letter, String achtZiffern) {
        return letter + achtZiffern + kvnrPruefziffer(letter, achtZiffern);
    }

    /**
     * Luhn-Pruefziffer (mod 10) ueber eine rein numerische Zeichenkette.
     * Wird hier fuer IK und ICCSN genutzt, um formal gueltige Nummern zu erzeugen.
     *
     * @param ziffern die Basisziffern (ohne Pruefziffer)
     * @return die anzuhaengende Pruefziffer (0-9)
     */
    public static int luhnPruefziffer(String ziffern) {
        if (!ziffern.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Nur Ziffern erlaubt: " + ziffern);
        }
        int summe = 0;
        boolean verdoppeln = true; // die Stelle direkt vor der Pruefziffer wird verdoppelt
        for (int i = ziffern.length() - 1; i >= 0; i--) {
            int ziffer = ziffern.charAt(i) - '0';
            if (verdoppeln) {
                ziffer *= 2;
                if (ziffer > 9) {
                    ziffer -= 9;
                }
            }
            summe += ziffer;
            verdoppeln = !verdoppeln;
        }
        return (10 - (summe % 10)) % 10;
    }

    /** Haengt an die Basisziffern die Luhn-Pruefziffer an. */
    public static String mitLuhn(String ziffern) {
        return ziffern + luhnPruefziffer(ziffern);
    }
}
