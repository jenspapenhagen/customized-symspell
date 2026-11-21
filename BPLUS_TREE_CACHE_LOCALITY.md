# B+-Baum / Cache-Lokalität (Valkey fbtree) — Analyse für customized-symspell

Quelle: <https://valkey.io/blog/from-skiplist-to-b+tree/> (16.11.2026),
Valkey 9.2 fbtree (Feature B+ Tree, 61-way Fanout).

## 1. Was der Blog beschreibt

Valkey ersetzt Skiplisten durch einen B+-Baum (fbtree) für Sorted Sets:

- **Problem:** Skiplist-Nodes sind einzeln allokiert und verstreut im Heap → 2,4–4,8×
  mehr Cache-Misses als B-Baum (Studie 2025). Pro Node ~37 Byte Pointer/Span-Overhead.
- **Lösung:** B+-Baum mit 61-way Fanout, kontiguierliche Leaf-Nodes, SIMD für
  Feature-Vergleich in Inner Nodes, linked Leaves für Range-Scans, O(1) Leftmost/
  Rightmost für Pops.
- **Ergebnis:** 36–43 % weniger Speicher pro Member, ZADD +105 %, ZREM +76 %,
  ZCOUNT +27 %, ZRANK +19 %.

## 2. Relevanz für dieses Repo

### Was passt

Die **Kernidee** — verstreute Allokationen durch kontiguierliche Speicherung ersetzen,
um Cache-Misses zu reduzieren — ist auf das Repo übertragbar. Die relevanten
verstreuten Strukturen:

| Struktur | Typ | Verstreut? |
|---|---|---|
| `deleteBuckets` | `int[][]` (Array von int-Arrays) | **Ja** — jede Bucket-Allokation separat |
| `terms` | `String[]` | Nein — ein Array |
| `frequencies` | `double[]` | Nein — ein Array |
| `deleteIndex` | `LongIntMap` (open addressing) | Nein — ein Array |
| `consideredSuggestionIds` | `IntHashSet` | Nein — ein Array |

`deleteBuckets` ist die einzige verstreute Struktur im Hot Path. Jeder Bucket ist ein
separates `int[]`-Objekt, das irgendwo im Heap liegt. Bei der Lookup-Schleife
(`for (int deleteId : deleteIds)`) wird das Bucket-Array geladen; der nächste
Bucket liegt an einer beliebigen Adresse.

### Was nicht passt

| Valkey-Optimierung | Relevanz für SymSpell |
|---|---|
| Sorted Set / Rank-Queries | Nicht vorhanden — SymSpell nutzt Hash-Maps, keine geordneten Strukturen |
| Skiplist → B+-Baum | Nicht anwendbar — kein Skiplist im Repo |
| SIMD für Feature-Vergleich in Inner Nodes | Bereits als Vector-API-Idee dokumentiert (SPEED_OPTIMIZATION_OPPORTUNITIES.md § 3.1) |
| Linked Leaves für Range-Scans | Nicht vorhanden — SymSpell scannt keine Ranges |
| O(1) Leftmost/Rightmost für Pops | Nicht vorhanden — keine Pop-Operationen |
| Range Deletions | Nicht vorhanden |
| Speicherreduktion (36–43 %) | User: RAM irrelevant |

## 3. Cache-Line-Flattening (bereits dokumentiert)

Die gleiche Idee wurde bereits in `SPEED_OPTIMIZATION_OPPORTUNITIES.md` § 3.4 als
„Cache-Line-Flattening der Delete-Buckets“ erfasst:

- **Idee:** `int[][] deleteBuckets` → ein flaches `int[]` mit Offset/Length-Paaren.
- **Erwartung:** 2–4 % Lookup-Gewinn durch bessere Cache-Lokalität.
- **Aufwand:** hoch (Refactor von `InMemoryDataHolder` + `lookupInMemory`, variable
  Bucket-Längen, Sortierung bei Build).
- **Risiko:** mittel (Korrektheit der Bucket-Adressierung).

Der Valkey-Blog bestätigt die zugrundeliegende These (kontiguierliche Speicherung
reduziert Cache-Misses signifikant), ändert aber nichts an der Bewertung: der Gewinn
ist klein (2–4 %), der Aufwand hoch.

## 4. Bewertung

- **Die B+-Baum-Idee selbst ist nicht direkt übertragbar.** SymSpell nutzt keine
  geordneten Strukturen, keine Ranks, keine Range-Scans. Ein B+-Baum würde die
  falsche Datenstruktur für den falschen Anwendungsfall einführen.
- **Die zugrundeliegende Cache-Lokalitäts-Idee ist bereits dokumentiert** als
  Cache-Line-Flattening. Der Valkey-Blog bestätigt die These, ändert aber die
  Gewinn-Schätzung nicht.
- **SIMD in Inner Nodes** → gleiche Idee wie Vector API (bereits dokumentiert).
- **Speicherreduktion** → User: irrelevant.

## 5. Einordnung

**Zu den „noch nicht ausprobierten Ideen“ zählen, nicht zu den Fehlversuchen.**
Die Idee ist nicht gescheitert — sie ist noch nicht implementiert und gemessen.
Sie gehört in dieselbe Kategorie wie Cache-Line-Flattening (§ 3.4 in
`SPEED_OPTIMIZATION_OPPORTUNITIES.md`): plausibel, aber ungemessen, mit hohem
Aufwand und kleinem erwartetem Gewinn.

## 6. Empfehlung

Kein Code-Change. Wenn Cache-Line-Flattening irgendwann implementiert und gemessen
wird, kann der Valkey-Blog als Referenz für die Cache-Miss-Reduktion zitiert werden.
Bis dahin: dokumentiert, aber nicht adoptiert.

## 7. Messkommandos

```bash
# Baseline für Cache-Line-Flattening (falls implementiert)
CP="symspell-lib/target/classes:symspell-lib/target/test-classes:$(cat /tmp/opencode/cp.txt)"
java -cp "$CP" io.github.mightguy.spellcheck.symspell.benchmark.LookupPerf \
    frequency_dictionary_en_82_765.txt noisy_query_en_1000.txt 5 30000
```
