# Plan: Speed-Optimierungen messen und vergleichen

## 1. Ziel

Die verbleibenden Speed-Ideen aus `SPEED_OPTIMIZATION_OPPORTUNITIES.md` und
`BPLUS_TREE_CACHE_LOCALITY.md` isoliert und kombiniert messen, um zu bestimmen,
welche sich lohnen. Startup und RAM sind explizit ausgeschlossen (User-Vorgabe).

## 2. Messbasis (unveränderlich)

| Parameter | Wert |
|---|---|
| Harness | `LookupPerf` (82.765 Wörter, 1.000 Queries, 5 Runden, beste Runde) |
| Metrik | TOP µs/Query (Median von 5 unabhängigen Läufen) |
| Baseline (aktuell) | TOP 3,32 – 3,49 µs, ALL 15,46 – 19,84 µs |
| JVM | JDK 25.0.4 Temurin, G1, Standard-Flags |
| Build | `mvn -q -pl symspell-lib compile -DskipTests` |
| CP | `symspell-lib/target/classes:symspell-lib/target/test-classes:$(cat /tmp/opencode/cp.txt)` |

## 3. Experiment-Reihenfolge

Jedes Experiment wird isoliert gemessen, dann kombiniert mit den vorherigen
Gewinnern.

### Exp 1: Vector API (SIMD) für `regionMatches` und `sameTrailingCharacters`

- **Idee:** Char-by-Char-Vergleich durch SIMD ersetzen (16 chars/Instruktion).
- **Betroffene Methoden:** `filterOnEquivalance` (8,9 %), `isDistanceCalculationRequired` (4,9 %).
- **Implementierung:** `jdk.incubator.vector` Vector API, `--add-modules jdk.incubator.vector`.
  - `regionMatches`: `CharVector.fromArray` + `compare` + `allTrue()`.
  - `sameTrailingCharacters`: gleiche Technik.
- **Erwartung:** 2–7 % (nur bei Strings ≥ 16 Zeichen; typische Wörter 3–12 → möglicherweise kein Gewinn).
- **Risiko:** Preview-API, Modul-Pfad, API-Änderung in JDK 26.
- **Messung:** TOP µs vor/nach, 5 Läufe, Median.

### Exp 2: Cache-Line-Flattening der Delete-Buckets

- **Idee:** `int[][] deleteBuckets` → flaches `int[]` mit Offset/Length.
- **Betroffene Methode:** `lookupInMemory` (innerste Schleife).
- **Implementierung:** `InMemoryDataHolder` + `lookupInMemory` umbauen.
  - Build: Buckets in ein flaches Array schreiben, Offsets in separatem `int[]`.
  - Lookup: `deleteIds = flatBuckets; offset = bucketOffsets[bucket]; length = bucketLengths[bucket]`.
- **Erwartung:** 2–4 % (bessere Cache-Lokalität).
- **Risiko:** hoch (Refactor, variable Bucket-Längen, Sortierung bei Build).
- **Messung:** TOP µs vor/nach, 5 Läufe, Median.

### Exp 3: PGO via AOT-Cache mit Trainingsdaten

- **Idee:** Trainingslauf mit LookupPerf, dann AOT-Cache nutzen.
- **Implementierung:** `java -XX:AOTCacheOutput=app.aot` mit Trainingsdaten, dann
  `java -XX:AOTCache=app.aot`.
- **Erwartung:** 2–5 % (besseres Inlining durch PGO).
- **Risiko:** gering (keine Codeänderung).
- **Messung:** TOP µs vor/nach, 5 Läufe, Median.

### Exp 4: Kombination der Gewinner

- Nur die Experimente, die einen messbaren Gewinn (> 1 %) zeigen, kombinieren.
- Messen und gegen Baseline + Einzelgewinne vergleichen.

## 4. Erfolgskriterien

| Ergebnis | Aktion |
|---|---|
| Gewinn > 5 % | Adoptieren, dokumentieren in `OPTIMIZATIONS.md` |
| Gewinn 1–5 % | Fallweise: Aufwand vs. Gewinn abwägen |
| Gewinn < 1 % oder negativ | Nicht adoptieren, als Fehlversuch dokumentieren |
| Kombination < Summe der Einzelgewinne | Synergie-Effekt notieren |

## 5. Ablauf

1. Baseline stabil messen (5 Läufe, Median) → `BASELINE.md`-Eintrag.
2. Exp 1 implementieren → messen → dokumentieren → commit (oder revert bei negativ).
3. Exp 2 implementieren → messen → dokumentieren → commit (oder revert bei negativ).
4. Exp 3 durchführen → messen → dokumentieren.
5. Exp 4: Gewinner kombinieren → messen → dokumentieren.
6. Finale Zusammenfassung in `OPTIMIZATIONS.md`.

## 6. Voraussetzungen

- JDK 25.0.4 Temurin (verfügbar).
- `mvn` 3.8.7 (verfügbar).
- `--add-modules jdk.incubator.vector` für Exp 1.
- Keine weiteren Dependencies nötig.

## 7. Risiken

- **Vector API Preview:** API kann in JDK 26 brechen. Experiment isoliert halten.
- **Cache-Line-Flattening:** Großer Refactor, hohe Regression-Risk. Tests müssen grün bleiben.
- **PGO:** Trainingsdaten müssen repräsentativ sein. LookupPerf nutzt 1.000 zufällige Queries.
- **Messrauschen:** TOP ist stabil (±2 %), ALL variiert stark (±15 %). Nur TOP für Entscheidungen nutzen.
