# String Deduplication (G1, JDK 8+) — Analyse für customized-symspell

Quelle: JEP 192, `-XX:+UseStringDeduplication -XX:+UseG1GC`.

## 1. Was ist das?

Der G1-Garbage-Collector kann bei einem Minor-GC identische String-Inhalte
deduplizieren: Wenn zwei `String`-Objekte dieselbe `char[]`/`byte[]` tragen, wird
eine Referenz auf ein gemeinsames Array gesetzt. Das spart Heap, wenn viele
duplizierte Strings existieren. Voraussetzung: G1-GC und das Flag
`-XX:+UseStringDeduplication`.

## 2. Relevanz für dieses Repo

Das 500k-Wörterbuch (`de-500k.txt`) enthält 500.000 **unterschiedliche** Wörter.
Die Delete-Indizes speichern `String`-Keys (die Deletes), die ebenfalls überwiegend
unterschiedlich sind. Duplikate entstehen fast nur durch:
- Präfix-Überlappungen (z. B. `haus`, `hausen`, `hausbau` teilen Präfixe, aber
  nicht ganze Strings),
- Bigramm-Bestandteile, die einzeln auch als Unigramme vorkommen.

Erwartung: geringer Effekt, weil die Duplikatrate niedrig ist.

## 3. Messung

Basis: `HeapBench` über `de-500k.txt`, 2 Läufe, 5× `System.gc()` vor der Messung.

| Variante | Build (ms) | Heap (MB) | Total-Memory (MB) |
|---|---:|---:|---:|
| Baseline (G1, Default) | 3.874 – 3.909 | 278 | 976 |
| `+UseStringDeduplication` | 4.062 | **278** | 1.016 |

- **Heap: keine Reduktion** (278 MB → 278 MB). Der Deduplizierer findet keine
  nennenswerten Duplikate, weil die Wörter und Deletes überwiegend einzigartig sind.
- **Build-Zeit: +5 %** (3,9 s → 4,1 s). Der Deduplizierer arbeitet bei jedem
  Minor-GC und kostet CPU, ohne Heap zu sparen.
- **Total-Memory: +4 %** (976 → 1.016 MB), weil G1 zusätzliche Strukturen für
  die Deduplizierung hält.

## 4. Bewertung

- **Kein Heap-Gewinn.** Die Annahme aus Abschnitt 2 bestätigt sich: Duplikate sind
  im Wörterbuch und im Delete-Index selten.
- **Kosten ohne Gegenwert:** +5 % Build-Zeit und +4 % Total-Memory.
- String Deduplication lohnt sich bei Workloads mit vielen identischen Strings
  (Web-Server, XML-Parser, HTTP-Header). Für ein Spell-Checker-Wörterbuch mit
  überwiegend einzigartigen Termen nicht.

## 5. Empfehlung

**Nicht adoptieren.** Flag weglassen. Wenn man Speicher sparen will, ist
Compact Object Headers (siehe `COMPACT_OBJECT_HEADERS.md`) die bessere Wahl
(−5 % Heap ohne Laufzeitkosten).

## 6. Messkommandos

```bash
java -XX:+UseStringDeduplication -XX:+UseG1GC -cp "/tmp/opencode:$CP" HeapBench \
    symspell-service/target/classes/de-500k.txt
```
