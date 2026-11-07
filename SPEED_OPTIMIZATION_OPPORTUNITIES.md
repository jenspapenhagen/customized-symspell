# Geschwindigkeit: verbleibende Optimierungsmöglichkeiten — Analyse für customized-symspell

Dieses Dokument ergänzt `OPTIMIZATIONS.md` (Abschnitt 13 und 14) um eine ehrliche
Bestandsaufnahme: Was wurde schon versucht, was sind die verbleibenden Hotspots,
und welche Ideen sind noch nicht ausprobiert. Ergebnis vorweg: **Der Code ist am
Ende dessen, was der JVM-Auto-Optimizer (C2) noch aus Standard-Java herausholt.
Weitere Gewinne erfordern entweder SIMD (Vector API, Preview), PGO (AOT-Cache mit
Trainingsdaten) oder algorithmische Änderungen, die jeweils eigene Risiken tragen.**

## 1. Ausgangslage

Messbasis (82.765 Wörter, `LookupPerf`, 5 Runden, beste Runde):

| Metrik | Wert |
|---|---:|
| TOP (µs/Query) | 3,32 – 3,49 |
| CLOSEST (µs/Query) | 3,57 – 4,10 |
| ALL (µs/Query) | 15,46 – 19,84 |
| Heap | 1 MB |

JFR-Hotspots (Lookup-Phase, aus `OPTIMIZATIONS.md` § 13):

| Methode | Anteil |
|---|---:|
| `SymSpellCheck.lookup` | 12,2 % (inkl. inlined Probe) |
| `filterOnEquivalance` | 8,9 % |
| `isDistanceCalculationRequired` | 4,9 % |
| `bitParallelOsa` | 4,1 % |
| Build: `addItem` | 8,1 % |
| Build: `HashSet.add` | 10,6 % |
| Build: `HashMap.resize` | 2,4 % |

Die verbleibenden ~70 % verteilen sich auf viele kleine Methoden (Array-Zugriffe,
Hash-Map-Probing, String-Zugriffe) — kein einzelner Hotspot dominiert.

## 2. Bereits versucht und gescheitert (aus § 14)

| Experiment | Ergebnis | Grund |
|---|---|---|
| Distanz-Cache (ConcurrentHashMap) | ALL +1 µs | Overhead |
| Aggressives `||`-Pruning | ALL 18,66 µs | durch kombinierte Bedingung ersetzt |
| q-Gram-Pre-Filter (Trigramme) | TOP 4,2 vs 3,7 µs | Trigram-Scan-Overhead |
| SWAR-Decoder (8/32/64) | keine Verbesserung | typische Dictionaries zu klein |
| Memory-Mapped / paralleler Import | kein Vorteil | < 100k Einträge |
| Front-Coding (`byte[]` statt `String[]`) | langsamer | Dekodierung pro Kandidat |
| TopKCollector (primitive Arrays) | kein Gewinn | bestehende Verwaltung deckt ab |
| Varint-Persistenz | kein Hot-Path-Gewinn | nur Startup |
| Group-Maxima-Pruning | kein Gewinn | Buckets klein (~40 Einträge) |
| XXH32 (char-basiert) | 31 vs 28 ns/op | nicht schneller als Murmur3 |
| `String.hashCode` als Delete-Hash | TOP 3,62 µs | schlechtere Verteilung → größere Buckets |
| Perfect Hash für Whitespace | in situ < 0,1 % | Prädikat nicht im Hot Path |
| Byte-Whitespace klassisch vs. Bitmap | klassisch schneller | 2,0 vs 4,2 ns/Byte |
| Tokenizer `split` → manuell | 130 ns gespart | 0,0004 % von 30 ms Lookup |
| String Deduplication | kein Heap-Gewinn | Duplikate selten |
| Compact Object Headers | −5 % Heap | kein Laufzeitgewinn |
| Memory-mapped Dictionary | < 1 % Build-Zeit | Lesen ist 1 % der Build-Zeit |

## 3. Verbleibende, noch nicht ausprobierte Ideen

### 3.1 Vector API (SIMD) — `jdk.incubator.vector`

**Was:** JDK 25 bietet die Vector API (Preview, `--add-modules jdk.incubator.vector`)
für explizite SIMD-Operationen (AVX2/AVX-512). Kandidaten im Repo:

- `regionMatches` (char-by-char-Vergleich in `filterOnEquivalance`): könnte 16
  chars pro Instruktion vergleichen statt 1.
- `sameTrailingCharacters`: gleiche Gelegenheit.
- `Utf16HashFunction.hash`: SIMD über den char-Array.

**Erwartung:** Die betroffenen Methoden sind 8,9 % + 4,9 % = 13,8 % des Profils.
SIMD könnte diese halbieren → ~7 % Gesamtgewinn. **Aber:** Die Strings sind kurz
(typisch 3–12 Zeichen). SIMD-Overhead (Setup, Tail-Handling) überwiegt bei
solchen Längen oft. Erst ab ~16 Zeichen lohnt sich Vektorisierung.

**Risiko:** Preview-API → `--add-modules jdk.incubator.vector` im Build und in der
Laufzeit nötig; Modul-Pfad-Kompatibilität in CI/CD; API kann sich in JDK 26
ändern. Kein Commit ohne Messung.

**Status:** nicht implementiert, nicht gemessen.

### 3.2 Profile-Guided Optimization (PGO) via AOT-Cache mit Trainingsdaten

**Was:** JDK 25's AOT Cache (JEP 483) kann mit Trainingsdaten (`-XX:AOTCacheOutput`
mit `java -Xshare:dump`-ähnlichem Workflow) hot methods aggressiver inlinen und
optimieren. Das ist keine Codeänderung, sondern ein Build-/Deployment-Schritt.

**Erwartung:** 10–30 % schnellerer Startup, aber auch besseres Inlining im
Steady-State (PGO). Für Lookup-Latenz könnte das 2–5 % bringen, weil C2 mit
Profilinformationen bessere Inlining-Entscheidungen trifft.

**Status:** Trainingslauf erzeugt Cache (10,7 MB), aber kein fairer Vorher/Nachher-
Vergleich über LookupPerf möglich (Dateipfad-basierte Testdaten). Siehe
`AOT_CACHE.md`.

### 3.3 JIT-Compiler-Tuning

**Was:** Flags wie `-XX:+UseSuperWord` (Auto-Vektorisierung, default on),
`-XX:+OptimizeStringConcat`, `-XX:CompileThreshold`, `-XX:+TieredCompilation`.

**Erwartung:** Diese sind in JDK 25 bereits Defaults. Manuelle Tuning bringt
typischerweise 0–2 % und kann bei falscher Konfiguration schaden. Kein
lohnender Ansatz.

**Status:** nicht getestet, geringe Erwartung.

### 3.4 Cache-Line-Optimierung der Delete-Buckets

**Was:** `deleteBuckets` ist `int[][]` — ein Array von int-Arrays. Jede Bucket-
Allokation ist verstreut im Heap. Flattening zu einem einzigen `int[]` mit
Offset/Length-Paaren würde die Cache-Lokalität verbessern, weil aufeinander-
folgende Buckets im Speicher benachbart liegen.

**Erwartung:** Die Lookup-Schleife iteriert über `deleteIds` (die Bucket-Inhalte).
Bei flachem Layout wäre der nächste Bucket im L1-Cache. Gewinn geschätzt 2–4 %.

**Risiko:** Großer Refactor von `InMemoryDataHolder` und `lookupInMemory`.
Die `deleteBuckets` werden beim Build sortiert (nach Frequenz), was das
Flattening kompliziert (variable Längen, Insertion-Order).

**Status:** nicht implementiert.

### 3.5 Algorithmische Verbesserung: besserer Pruning

**Was:** `isDistanceCalculationRequired` prüft, ob die ersten und letzten Zeichen
des Kandidaten und der Suggestion übereinstimmen. Wenn nicht, wird die
Edit-Distanz übersprungen. Diese Prüfung könnte erweitert werden:

- **Bigram-Pruning:** Prüfe, ob mindestens ein Bigramm des Kandidaten im
  Suggestion vorkommt. Wenn nicht, überspringe.
- **Position-basiertes Pruning:** Prüfe mehr als nur das erste/letzte Zeichen.

**Erwartung:** Wenn mehr Kandidaten übersprungen werden, sinkt die Zahl der
`bitParallelOsa`-Aufrufe (4,1 %). Aber `bitParallelOsa` ist bereits schnell
(bit-parallel, O(n/64)). Der Gewinn wäre vielleicht 1–2 %.

**Risiko:** Falsch-Positive Pruning (Kandidaten werden übersprungen, die eigentlich
gültig wären) → falsche Ergebnisse. Sehr hohes Risiko.

**Status:** nicht implementiert.

### 3.6 `int`-basierte Edit-Distanz für Einheitsgewichte

**Was:** Wenn alle Gewichte 1,0 sind (`unitCost = true`), ist die Edit-Distanz
immer eine ganze Zahl. Die API verwendet `double` durchgängig. Ein `int`-basierter
Pfad (ohne Floating-Point-Operationen) wäre schneller.

**Erwartung:** Floating-Point-Operationen sind auf modernen CPUs fast so schnell
wie Integer. Der Gewinn wäre marginal (1–2 %). Der Refactor-Aufwand ist groß
(alle Aufrufstellen, `SuggestionItem.distance`, `Composition`).

**Status:** nicht implementiert.

## 4. Empfehlung

Die realistischen Gewinne sind:

| Idee | Geschätzter Gewinn | Aufwand | Risiko |
|---|---|---|---|
| Vector API (SIMD) | 2–7 % | mittel | Preview-API |
| PGO via AOT-Cache | 2–5 % | gering | gering |
| Cache-Line-Flattening | 2–4 % | hoch | mittel |
| Besserer Pruning | 1–2 % | mittel | hoch (Korrektheit) |
| `int`-basierte Distanz | 1–2 % | hoch | gering |

**Keine dieser Ideen rechtfertigt einen Code-Change ohne vorherige Messung.** Die
Projekt-Praxis (siehe `OPTIMIZATIONS.md` § 14) ist: messen, dann entscheiden.
Die bisherigen Experimente zeigen, dass die JVM bereits exzellent optimiert, und
dass die meisten „offensichtlichen“ Optimierungen (q-Gram, Front-Coding, Caching)
netto langsamer sind.

**Nächster sinnvoller Schritt:** Vector API in einem isolierten Experiment
ausprobieren — ein Microbenchmark, der `regionMatches` mit und ohne SIMD vergleicht,
auf Strings der Längen 4, 8, 16 und 32. Wenn SIMD bei Länge ≥ 16 einen klaren
Gewinn zeigt, lohnt sich die Integration. Wenn nicht, ist das Thema abgeschlossen.

## 5. Messkommandos

```bash
# Baseline (stabil, median von 5 Läufen)
CP="symspell-lib/target/classes:symspell-lib/target/test-classes:$(cat /tmp/opencode/cp.txt)"
for i in 1 2 3 4 5; do
    java -cp "$CP" io.github.mightguy.spellcheck.symspell.benchmark.LookupPerf \
        frequency_dictionary_en_82_765.txt noisy_query_en_1000.txt 5 30000 2>&1 | grep TOP
done
```
