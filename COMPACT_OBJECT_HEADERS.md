# Compact Object Headers (JDK 25) — Analyse für customized-symspell

Quelle: JEP 519 (JDK 25), `java -XX:+UseCompactObjectHeaders`.

## 1. Was ist das?

Bis JDK 24 belegt der Objekt-Header auf 64-Bit-JVMs 12 Byte (Mark Word 8 Byte +
Klassenzeiger 4 Byte mit Compressed OOPs). JDK 25 führt **Compact Object Headers**
ein: Der Header schrumpft auf 8 Byte, weil Mark Word und Klassenzeiger in einem
64-Bit-Wort zusammengefasst werden. Das spart pro Objekt 4 Byte und verbessert die
Cache-Dichte. Es ist kein Preview-Flag mehr; `-XX:+UseCompactObjectHeaders` steht
ohne `--enable-preview` zur Verfügung.

## 2. Erwarteter Effekt

- Heap: −4 Byte pro Objekt. Bei 500.000 Wörtern plus Millionen von Delete-Einträgen
  (int[]-Buckets, String-Objekte, HashSet-Generations) ist das eine spürbare
  Reduktion.
- Cache: dichtere Objekt-Layouts → weniger Cache-Misses bei Traversal.
- Laufzeit: potenziell schneller bei speicherintensiven Workloads; bei CPU-gebundenen
  Hot Loops (Edit-Distanz) kein direkter Effekt.

## 3. Messung

Basis: `HeapBench` über `de-500k.txt` (500.000 Wörter, `InMemoryDataHolder`,
`prefixLength` 7, `maxEditDistance` 2), 2 Läufe pro Variante, bester Lauf berichtet,
5× `System.gc()` vor der Messung.

| Variante | Build (ms) | Heap (MB) | Total-Memory (MB) |
|---|---:|---:|---:|
| Baseline (G1, Default) | 3.874 – 3.909 | 278 | 976 |
| `+UseCompactObjectHeaders` | 3.964 – 4.068 | **264** | 936 – 960 |

- **Heap: −14 MB (−5 %)** — konsistent über beide Läufe (264 MB vs. 278 MB).
- Build-Zeit: innerhalb der Messungenauigkeit (±3 %), kein signifikanter Unterschied.
- Lookup-Latenz: `LookupPerf` (82.765 Wörter) zeigt keine klare Verbesserung
  (TOP 4,8–6,3 µs mit und ohne Flag, Rauschen dominiert).

## 4. Bewertung

- **Heap-Gewinn ist real und konsistent** (−5 %). Auf der 304-MB-Basis des
  Web-Demos entspricht das ~15 MB frei — nicht dramatisch, aber kostenlos.
- **Kein Laufzeitgewinn messbar** bei der Lookup-Latenz; der Hot Path ist
  CPU-gebunden (Edit-Distanz, Kandidatenverifikation), nicht speicherlimitiert.
- **Risiko: gering.** Compact Object Headers sind in JDK 25 als Produkt-Feature
  verfügbar und werden von Oracle für Produktionseinsatz empfohlen. Es gibt
  inkompatibelkeitsfreie Warnungen nur bei nativen Code, der Header-Layouts annimmt
  (JNI, Unsafe) — das Projekt nutzt keine solchen Stellen (nur `MemorySegment`/FFM,
  das unabhängig vom Header-Layout arbeitet).

## 5. Empfehlung

Als JVM-Flag für den Service und die Demo adoptieren, ohne Codeänderung:

```bash
java -XX:+UseCompactObjectHeaders -jar symspell-service.jar
```

In `.github/workflows/ci.yml` und der README-Dokumentation als optionales Flag
ergänzen. Keine Codeänderung nötig; kein Checkstyle/SpotBugs-Auswirkung.
Kein Commit-Pflicht, weil reine Laufzeitkonfiguration.

## 6. Wiederverwendung / Messkommandos

```bash
# Build-Klassenpfad
mvn -q -pl symspell-lib dependency:build-classpath -Dmdep.outputFile=/tmp/opencode/cp.txt
# Heap-Messung (500k Wörter)
CP="symspell-lib/target/classes:symspell-lib/target/test-classes:$(cat /tmp/opencode/cp.txt)"
javac -cp "$CP" -d /tmp/opencode /tmp/opencode/HeapBench.java
java -XX:+UseCompactObjectHeaders -cp "/tmp/opencode:$CP" HeapBench \
    symspell-service/target/classes/de-500k.txt
```
