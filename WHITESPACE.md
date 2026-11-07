# Whitespace-Prüfung per Perfect Hash — Algorithmus und Bewertung für dieses Repo

Quelle: <https://serce.me/posts/2026-10-30-how-hard-is-it-to-check-for-whitespace>
(30.10.2026), inklusive der dort verlinkten Guava-Historie und der Unicode-Daten.

Dieses Dokument beschreibt den Algorithmus exakt, vergleicht die Whitespace-Definitionen
von Java, Unicode, Python, JavaScript und regulären Ausdrücken und bewertet, ob der
Perfect Hash für `customized-symspell` etwas bringt. Ergebnis vorweg: **nein** — die
Begründung steht in Abschnitt 8 und 9. Der Algorithmus selbst ist trotzdem vollständig
dokumentiert, damit die Entscheidung nachvollziehbar bleibt und der Code bei Bedarf
kopiert werden kann.

## 1. Problemstellung

Eine Funktion `isWhitespace(char c)` wird in Parsern und Tokenizern pro Zeichen
aufgerufen. Die naheliegende Implementierung ist eine Kette von Vergleichen:

```java
static boolean isWhitespace(final char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
}
```

Für die 25 Zeichen der Unicode-Eigenschaft `White_Space` wären das 25 Vergleiche oder
ein `switch`. Beides ist langsam und schlecht vorhersagbar, weil ein `switch` über
verstreute Werte zu einem Binärbaum oder zu Sprungtabellen mit vielen Zweigen wird.
Guava löst das mit einem **Perfect Hash**: einer konstanten Multiplikation, einem
Rechtsshift und einem einzelnen Zeichenvergleich.

## 2. Der Algorithmus (Guava `CharMatcher.Whitespace`)

```java
private static final String TABLE =
        "\u2002\u3000\r\u0085\u200A\u2005\u2000\u3000"
        + "\u2029\u000B\u3000\u2008\u2003\u205F\u3000\u1680"
        + "\u0009\u0020\u2006\u2001\u202F\u00A0\u000C\u2009"
        + "\u3000\u2004\u3000\u3000\u2028\n\u2007\u3000";
private static final int MULTIPLIER = 1682554634;
private static final int SHIFT = Integer.numberOfLeadingZeros(TABLE.length() - 1);

public boolean matches(final char c) {
    return TABLE.charAt((MULTIPLIER * c) >>> SHIFT) == c;
}
```

Schritt für Schritt:

1. **`TABLE.length()` ist eine Zweierpotenz.** 32 Zeichen, also `TABLE.length() - 1 = 31`.
   `Integer.numberOfLeadingZeros(31)` ist 27, folglich `SHIFT = 27`.
2. **`MULTIPLIER * c`** ist eine `int`-Multiplikation, die bewusst überläuft. Java
   rechnet modulo 2³², das Ergebnis ist also die unteren 32 Bits des Produkts.
3. **`>>> SHIFT`** verschiebt logisch um 27 Stellen nach rechts und liefert damit die
   **obersten 5 Bits** des Produkts — einen Wert von 0 bis 31, den Slot-Index.
4. **`TABLE.charAt(slot)`** liest den Kandidaten an diesem Slot. Da `slot < 32`, ist der
   Zugriff immer im gültigen Bereich; eine Bereichsprüfung ist nicht nötig.
5. **`== c`** ist der eigentliche Test. Der Hash ist nur ein Filter: Steht an dem Slot
   genau das gesuchte Zeichen, ist es Whitespace; steht dort etwas anderes (Füllzeichen
   oder ein anderes Whitespace-Zeichen mit demselben Slot), ist es keins.

Der Trick ist also nicht, dass der Hash kollisionsfrei auf die Zeichen abbildet,
sondern dass die Tabelle **pro Slot genau einen Kandidaten** enthält und der
abschließende Vergleich Kollisionen und Fehltreffer eliminiert. Das Verfahren ist ein
„perfect hash“ im weiteren Sinn: eine Funktion `h: char -> [0, 32)`, für die jedes
gesuchte Zeichen auf einen Slot zeigt, der genau dieses Zeichen enthält.

Kosten pro Aufruf: eine Integer-Multiplikation, ein Shift, ein `charAt` (L1-Load) und
ein Vergleich — konstant, ohne Verzweigung außer dem Ergebnisvergleich.

### Warum 32 Slots bei 25 Zeichen?

25 Zeichen lassen sich in 32 Slots unterbringen, wenn der Multiplier passend gewählt
ist. Die 7 übrigen Slots werden mit einem Füllzeichen belegt. Guava nimmt dafür
`U+3000` (IDEOGRAPHIC SPACE), weil dieses Zeichen selbst ein Whitespace-Zeichen ist und
auf einen anderen Slot (26) zeigt: Ein Zugriff auf einen Füllslot liefert `U+3000`,
und `U+3000 == c` gilt nur für `c == U+3000`, das aber über Slot 26 läuft. Damit ist
jeder Füllslot garantiert ein Fehltreffer, ohne dass ein zweiter Vergleich nötig wäre.

Belegung der Guava-Tabelle (aus der oben zitierten Konstante):

| Slot | Zeichen | | Slot | Zeichen |
|---:|---|---|---:|---|
| 0 | U+2002 | | 16 | U+0009 |
| 1 | U+3000 (Füllung) | | 17 | U+0020 |
| 2 | U+000D | | 18 | U+2006 |
| 3 | U+0085 | | 19 | U+2001 |
| 4 | U+200A | | 20 | U+202F |
| 5 | U+2005 | | 21 | U+00A0 |
| 6 | U+2000 | | 22 | U+000C |
| 7 | U+3000 (Füllung) | | 23 | U+2009 |
| 8 | U+2029 | | 24 | U+3000 (Füllung) |
| 9 | U+000B | | 25 | U+2004 |
| 10 | U+3000 (Füllung) | | 26 | U+3000 |
| 11 | U+2008 | | 27 | U+3000 (Füllung) |
| 12 | U+2003 | | 28 | U+2028 |
| 13 | U+205F | | 29 | U+000A |
| 14 | U+3000 (Füllung) | | 30 | U+2007 |
| 15 | U+1680 | | 31 | U+3000 (Füllung) |

Freie Slots: 1, 7, 10, 14, 24, 27, 31.

## 3. Den Multiplier finden

Der Multiplier ist nicht analytisch hergeleitet, sondern wird gesucht. Für eine
Zweierpotenz-Tabelle der Länge 32 ist das ein kurzer Brute-Force-Lauf über alle
`int`-Werte:

```java
private static int findMultiplier(final char[] characters, final int shift) {
    for (int multiplier = 1; multiplier != 0; multiplier++) {
        final char[] table = new char[32];
        final boolean[] used = new boolean[32];
        boolean ok = true;
        for (final char c : characters) {
            final int slot = (multiplier * c) >>> shift;
            if (used[slot] && table[slot] != c) {
                ok = false;
                break;
            }
            used[slot] = true;
            table[slot] = c;
        }
        if (ok) {
            return multiplier;
        }
    }
    throw new IllegalStateException("no multiplier found");
}
```

Zwei Bedingungen muss der Kandidat erfüllen:

- Zwei **verschiedene** Zeichen dürfen nicht auf denselben Slot fallen
  (`used[slot] && table[slot] != c`). Dasselbe Zeichen mehrfach ist harmlos.
- Der Slot muss im Tabellenbereich liegen; bei 32 Slots und `shift = 27` ist das
  automatisch erfüllt, weil die oberen 5 Bits immer 0..31 ergeben.

Für die 25 Unicode-Whitespace-Zeichen ist der erste Treffer bei dieser Suche
`150812695`; Guava verwendet `1682554634`, ebenfalls ein gültiger Treffer (die Suche
bricht beim ersten Erfolg ab, Guava hat offenbar anders/weiter gesucht). Beide
Multiplier erzeugen eine korrekte Tabelle, nur mit anderer Slot-Reihenfolge.

## 4. Dieselbe Technik für die Java-Semantik

`Character.isWhitespace` ist **nicht** identisch mit `White_Space`. Wer das Prädikat
1:1 ersetzen will, braucht eine Tabelle über der Java-Menge. Java 25 liefert genau
25 Zeichen:

```
U+0009 U+000A U+000B U+000C U+000D U+001C U+001D U+001E U+001F U+0020
U+1680 U+2000 U+2001 U+2002 U+2003 U+2004 U+2005 U+2006 U+2008 U+2009
U+200A U+2028 U+2029 U+205F U+3000
```

Der mit derselben Suche gefundene Multiplier ist **`145474072`**, die Tabelle hat
wieder 32 Slots:

```java
private static final String TABLE =
        "\u001E\u001F \u1680\u3000\u3000\u3000\u3000\u3000\t\n\u000B\u3000\f\r"
        + "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u205F\u2008\u2009\u200A"
        + "\u2028\u2029\u3000\u3000\u001C\u001D";
private static final int MULTIPLIER = 145474072;
private static final int SHIFT = 27;

static boolean isWhitespace(final char c) {
    return TABLE.charAt((MULTIPLIER * c) >>> SHIFT) == c;
}
```

Achtung beim Notieren des Literals: `\u000A` und `\u000D` werden vom Java-Compiler
**vor** dem Parsen der String-Literale ersetzt und zerlegen das Literal in zwei Zeilen.
Deshalb stehen oben `\t`, `\n`, `\f` und `\r`; nur `\u000B` ist als Unicode-Escape
unproblematisch.

Slot-Belegung dieser Tabelle:

| Slot | Zeichen | | Slot | Zeichen |
|---:|---|---|---:|---|
| 0 | U+001E | | 16 | U+2001 |
| 1 | U+001F | | 17 | U+2002 |
| 2 | U+0020 | | 18 | U+2003 |
| 3 | U+1680 | | 19 | U+2004 |
| 4 | U+3000 (Füllung) | | 20 | U+2005 |
| 5 | U+3000 (Füllung) | | 21 | U+2006 |
| 6 | U+3000 | | 22 | U+205F |
| 7 | U+3000 (Füllung) | | 23 | U+2008 |
| 8 | U+3000 (Füllung) | | 24 | U+2009 |
| 9 | U+0009 | | 25 | U+200A |
| 10 | U+000A | | 26 | U+2028 |
| 11 | U+000B | | 27 | U+2029 |
| 12 | U+3000 (Füllung) | | 28 | U+3000 (Füllung) |
| 13 | U+000C | | 29 | U+3000 (Füllung) |
| 14 | U+000D | | 30 | U+001C |
| 15 | U+2000 | | 31 | U+001D |

Freie Slots: 4, 5, 7, 8, 12, 28, 29.

## 5. Verifikation

Ein Perfect Hash ist nur so gut wie seine Abdeckung. Der Test läuft über alle 65.536
`char`-Werte und vergleicht gegen die JDK-Implementierung:

```java
static void verify() {
    int errors = 0;
    for (int value = 0; value <= 0xFFFF; value++) {
        final char c = (char) value;
        if (isWhitespace(c) != Character.isWhitespace(c)) {
            errors++;
        }
    }
    if (errors != 0) {
        throw new AssertionError("mismatches: " + errors);
    }
}
```

Für die oben angegebene Tabelle mit `MULTIPLIER = 145474072` ist das Ergebnis
**0 Fehler**. Für die Guava-Tabelle gegen die Unicode-Menge ebenfalls 0 Fehler.

## 6. Semantik: fünf verschiedene Definitionen von „Whitespace“

Der Algorithmus ist nur die halbe Antwort. Die andere Hälfte ist die Frage, **welche**
Zeichen als Whitespace gelten. Die verbreiteten Definitionen unterscheiden sich:

| Zeichen | Unicode `White_Space` | Java `Character.isWhitespace` | Java-Regex `\s` | Python `str.isspace()` | JS `\s` |
|---|:--:|:--:|:--:|:--:|:--:|
| U+0009–U+000D | ja | ja | ja | ja | ja |
| U+0020 | ja | ja | ja | ja | ja |
| U+001C–U+001F | nein | **ja** | nein | **ja** | nein |
| U+0085 (NEL) | ja | **nein** | nein | **ja** | nein |
| U+00A0 (NBSP) | ja | **nein** | nein | **ja** | **ja** |
| U+1680 | ja | ja | nein | ja | ja |
| U+2000–U+200A | ja | ja (außer U+2007) | nein | ja | ja |
| U+2007 (figure space) | ja | **nein** | nein | ja | ja |
| U+2028, U+2029 | ja | ja | nein | ja | ja |
| U+202F (narrow NBSP) | ja | **nein** | nein | ja | ja |
| U+205F | ja | ja | nein | ja | ja |
| U+3000 | ja | ja | nein | ja | ja |
| U+FEFF (BOM/ZWNBSP) | nein | nein | nein | nein | **ja** |
| U+180E | nein (seit Unicode 6.3) | nein | nein | nein | nein |

Wichtige Konsequenzen:

- **`Character.isWhitespace` ≠ Unicode `White_Space`.** Java nimmt die
  Steuerzeichen U+001C–U+001F mit (historisch für Dateiformate) und lässt NBSP,
  U+2007 und U+202F weg, weil sie sichtbar sind und Wörter nicht trennen sollen.
- **Java-Regex `\s` ist ASCII-only**: `[ \t\n\x0B\f\r]`. Sie splittet weder NBSP noch
  U+2003 noch U+0085. `\s` mit `UNICODE_CHARACTER_CLASS` wäre die Unicode-Variante.
- **`String.trim()`** entfernt nur Zeichen `<= U+0020`, also auch Steuerzeichen wie
  U+0000, aber kein Unicode-Whitespace oberhalb. **`String.strip()`** nutzt
  `Character.isWhitespace` und entfernt zusätzlich U+2000 ff., aber kein NBSP.
  `String.isBlank()` verhält sich wie `strip()`.
- **Python `isspace()`** enthält NBSP und U+0085, **JavaScript `\s`** enthält NBSP und
  U+FEFF, aber nicht U+0085.
- **Unicode 6.3** hat U+180E (MONGOLIAN VOWEL SEPARATOR) aus `White_Space` entfernt;
  ältere Tabellen führen es noch.

Eine Tabelle, die für eine dieser Semantiken gebaut wurde, ist für die anderen falsch.
Das ist der häufigste Fehler bei der Übernahme solcher Codeschnipsel.

## 7. Messungen

Alle Messungen auf dieser Maschine mit JDK 25.0.4 (Temurin), Microbenchmarks ohne JMH
(mehrere Runden, beste Runde berichtet, `Blackhole` über ein `volatile`-Feld).

### Isoliertes Prädikat

| Variante | ns pro Zeichen |
|---|---:|
| `Character.isWhitespace(char)` | 1,52–1,56 |
| Perfect Hash, Unicode-Tabelle (Guava) | 0,72–0,78 |
| Perfect Hash, Java-Tabelle (145474072) | 0,76–0,79 |

Der Perfect Hash ist isoliert etwa **2× schneller**. Der Grund ist, dass
`Character.isWhitespace` intern Fallunterscheidungen und Lookup-Tabellen kombiniert,
während der Hash mit einer Multiplikation und einem Load auskommt. Beide Varianten
liegen im Bereich weniger Nanosekunden; der absolute Gewinn pro Aufruf ist unter 1 ns.

### Byte-Prädikat im Dictionary-Parser

Für den Parser zählt nur ASCII. Bei 20 % Whitespace-Anteil:

| Variante | ns pro Byte |
|---|---:|
| Kette aus 5 Vergleichen (`' ', '\t', '\n', '\r', '\f'`) | ~2,0 |
| Bereichsprüfung `(b >= 0x09 && b <= 0x0D) || b == ' '` | ~4,2 |
| 256-Bit-Bitmap (`long[4]`) | ~4,2 |

Die klassische Vergleichskette ist hier am schnellsten: Der Compiler faltet sie zu
wenigen Vergleichen, und die Verzweigungen sind bei Text gut vorhersagbar. Ein Perfect
Hash lohnt für `byte` nicht, weil die Tabelle nur 256 Einträge hätte und der
Tabellenzugriff teurer ist als zwei bis fünf Vergleiche.

### In situ

| Messung | Ergebnis |
|---|---|
| `DictionaryReader` über `de-500k.txt` (500.000 Zeilen, nur Lesen) | 40,3 ms |
| `lookupCompound`, ein verrauschtes Wort (82.765-Wörter-Dict) | 30,3–34,1 ms |
| `lookupCompound`, Drei-Wort-Phrase | 104–117 ms |
| `lookupCompound` ohne Tokenizer (`tokenizeOnWhiteSpace=false`) | 29,8–33,3 ms |
| `wordBreakSegmentation`, 42-Zeichen-Text | 806–850 µs |
| `split("\\s+")` pro Aufruf | ~200 ns |
| manueller Tokenizer pro Aufruf | 65–74 ns |

## 8. Aufrufstellen im Repo

| Stelle | Prädikat | Zeichenmenge | Häufigkeit |
|---|---|---|---|
| `DictionaryReader.isWhitespace(byte)` | `' ', '\t', '\n', '\r', '\f'` | 5, ASCII | 2–5 pro Zeile, ~1,3 Mio. bei 500k Zeilen |
| `SpellHelper.tokenizeOnWhiteSpace` | `word.split("\\s+")` | 6, ASCII | 1× pro `lookupCompound` |
| `SymSpellCheck.wordBreakSegmentation` | `Character.isWhitespace` | 25, Java | `n · maxSegLen` pro Text |
| `SuggestionService` | `query.strip()` | 25, Java | 1× pro HTTP-Request |
| Solr-Handler/Listener/Tests | `String.trim()` | `<= U+0020` | 1× pro Treffer |

Bewertung:

- **`DictionaryReader`**: Das Byte-Prädikat ist bereits die schnellste Variante
  (Abschnitt 7). Selbst wenn man es durch einen Perfect Hash ersetzte, würde man
  höchstens einen Bruchteil der 40,3 ms Lesen sparen — bei einem Build, der mit
  82.765 Wörtern 768 ms und mit 500.000 Wörtern etwa 4 s dauert. Kein messbarer Effekt.
- **`tokenizeOnWhiteSpace`**: `split("\\s+")` kostet ~200 ns, ein manueller Scanner
  ~70 ns. Die Ersparnis von ~130 ns geht im `lookupCompound`-Aufruf mit 30 ms
  vollständig unter (< 0,001 %). Zudem ist die Regex-Semantik (`\s` = ASCII) eine
  bewusste Entscheidung: `lookupCompound` soll an Leerzeichen, Tabs und Zeilenumbrüchen
  trennen, nicht an NBSP.
- **`wordBreakSegmentation`**: Der `Character.isWhitespace`-Aufruf passiert pro
  Teilstring-Start, also einige hundert Mal pro Text. Bei 0,8 ns Ersparnis pro Aufruf
  sind das < 0,5 µs auf 806–850 µs Gesamtlaufzeit, also < 0,1 %. Nicht messbar.
- **`strip()`/`trim()`**: Einmal pro Request, irrelevant.

Der Perfect Hash optimiert ein Prädikat, das im Gesamtprofil nicht auftaucht. Die
JFR-Hotspots des Projekts liegen in der Edit-Distanz, der Kandidatenverifikation und
der Delete-Generierung (siehe `OPTIMIZATIONS.md`), nicht in der Klassifikation von
Zeichen.

## 9. Ergebnis

**Es gibt mit diesem Algorithmus nichts zu holen.** Die Gründe:

1. Die einzige Stelle mit nennenswerter Aufrufzahl arbeitet auf `byte` und ist mit
   der Vergleichskette bereits optimal (2,0 ns/Byte gegenüber 4,2 ns/Byte für
   Tabellen- oder Bereichsvarianten).
2. Die Stellen mit `char` werden einige hundert bis wenige tausend Mal pro Operation
   aufgerufen. Der Gewinn von ~0,8 ns pro Aufruf liegt unter der Messgenauigkeit der
   Gesamtbenchmarks.
3. Der Perfect Hash würde eine zweite, gepflegte Zeichentabelle einführen, die bei
   einer Unicode-Aktualisierung oder einer Änderung der gewünschten Semantik
   mitgezogen werden muss. Das ist ein Wartungsrisiko ohne Gegenwert.
4. `split("\\s+")` durch einen manuellen Tokenizer zu ersetzen, wäre der einzige
   messbare Einzelgewinn (~130 ns), aber gemessen am 30-ms-Aufruf bedeutungslos.

Deshalb bleibt der Produktionscode unverändert. Dieses Dokument hält den Algorithmus
fest, falls er an anderer Stelle gebraucht wird — zum Beispiel in einem Parser, der
pro Zeichen über Unicode-Whitespace entscheiden muss und dabei Millionen Aufrufe
erreicht.

## 10. Kopiervorlage

Falls die Java-Semantik doch einmal gebraucht wird, hier die geprüfte Variante
(kein `var`, `static final`, verifiziert über alle 65.536 `char`-Werte):

```java
/**
 * Klassifiziert Zeichen mit derselben Semantik wie {@link Character#isWhitespace(char)},
 * aber über einen Perfect Hash statt über Fallunterscheidungen.
 */
public final class JavaWhitespace {

    private static final String TABLE =
            "\u001E\u001F \u1680\u3000\u3000\u3000\u3000\u3000\t\n\u000B\u3000\f\r"
            + "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u205F\u2008\u2009\u200A"
            + "\u2028\u2029\u3000\u3000\u001C\u001D";
    private static final int MULTIPLIER = 145474072;
    private static final int SHIFT = 27;

    private JavaWhitespace() {
    }

    /**
     * Prüft, ob das Zeichen nach Java-Semantik Whitespace ist.
     *
     * @param c das zu prüfende Zeichen
     * @return {@code true}, wenn {@link Character#isWhitespace(char)} zustimmen würde
     */
    public static boolean isWhitespace(final char c) {
        return TABLE.charAt((MULTIPLIER * c) >>> SHIFT) == c;
    }
}
```

Für die Unicode-Semantik (`White_Space`, 25 Zeichen) stattdessen die Guava-Tabelle
mit `MULTIPLIER = 1682554634` verwenden (Abschnitt 2). Für ASCII genügt die
Vergleichskette aus Abschnitt 1.
