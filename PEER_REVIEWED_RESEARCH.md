# Peer-Reviewed Research & Neue Algorithmen — Analyse für customized-symspell

Quelle: systematische Literaturrecherche (arXiv, IEEE, ACM, ACL, Springer, PMC, SeekStorm-Blog, GitHub), November 2026.

## 1. Suchstrategie

Gesucht wurde nach:
- Peer-reviewed Papers zu SymSpell-Optimierungen oder Symmetric Delete (2023–2026)
- Neue Datenstrukturen oder Algorithmen für Spell-Correction-Performance
- Cache-effiziente oder SIMD-basierte String-Matching-Verfahren
- Radix-Trie / B+-Baum / Hash-Map-Optimierungen für Fuzzy-Search

Durchsuchte Datenbanken: arXiv, IEEE Xplore, ACM DL, ACL Anthology, Springer,
PMC, SeekStorm-Blog, GitHub.

## 2. Gefundene Papers und Ergebnisse

### 2.1 Healthcare Query Spelling Correction (arXiv:2603.19249, 2026)

**Inhalt:** Erste kontrollierte Studie zu Spell-Correction als Retrieval-
Preprocessing in Healthcare-QA. 4 Methoden (Conservative Edit Distance, Standard
Edit Distance, Context-Aware, SymSpell) auf TREC 2017 LiveQA + HealthSearchQA.

**Relevantes Ergebnis:** SymSpell modifiziert nur 41/103 Queries (39,8 %) und
erzielt minimalen MRR-Gewinn (+0,008, 1,3 %). Die medizinische Vokabularstruktur
(lange Drug-Namen, lateinische/griechische Wurzeln) passt schlecht zu SymSpells
Delete-only-Kandidatengenerierung.

**Bewertung für dieses Repo:** Keine Speed-Optimierung. Die Studie bestätigt, dass
SymSpell bei bestimmten Vokabularen weniger Kandidaten generiert (konservativeres
Pruning), was die Lookup-Latenz senken könnte — aber das ist ein
Qualitäts-/Vokabularproblem, kein Algorithmus-Problem. Kein neuer Speed-Ansatz.

### 2.2 SymSpell v7 / SeekStorm (Rust, 2025)

**Quelle:** <https://seekstorm.com/blog/symspell_rs-3x-faster-lookup-40-less-memory-11x-faster-levenshtein>

**Kernergebnis (wörtlich):** „An 11x faster edit distance gave only a 20% speedup
for SymSpell. That told us two things: 1️⃣ SymSpell's makes spelling correction
latency largely independent of raw edit distance performance. The symmetric delete
algorithm already avoids most of that work. 2️⃣ Further performance gains had to
come from SeekStorm implementation improvements rather than edit distance calculation
optimization."

**Was SeekStorm tatsächlich gemacht hat (300 % Speedup):**
- Bit-paralleles OSA (Hyyrö 2003) — **bereits im Repo implementiert**
- Multi-Word-Block OSA für Terms > 64 Zeichen — **bereits implementiert** (banded DP)
- StringHashSet (Open-Addressing, Generationen) — **bereits implementiert**
- Term-ID-Speicher (int[][] Buckets) — **bereits implementiert**
- DictionaryReader (SWAR, TAB) — **bereits implementiert**
- Prefix-Indexing — **bereits implementiert** (prefixLength=7)

**Bewertung:** SeekStorm hat genau die Optimierungen gemacht, die dieses Repo auch
hat. Die 300 % Speedup kamen aus Implementierungsdetails (Rust, Cache-Lokalität,
Memory-Layout), nicht aus neuen Algorithmen. Keine neuen Ideen.

### 2.3 BK-Tree Triangle Inequality (Wolf Garbe, 2017)

**Quelle:** <https://seekstorm.com/blog/symspell-vs-bk-tree>

**Ergebnis (wörtlich):** „I also tested an integration of the BK-tree triangle
inequality principle into SymSpell as an additional step to further reduce/filter
the number of required Levenshtein calculations. There were only little performance
improvements, but increased memory requirement and precalculation costs."

**Bewertung:** Bereits bekannt und gescheitert. Steht in `OPTIMIZATIONS.md` § 14.

### 2.4 Cache-efficient Damerau-Levenshtein (Zhao & Sahni, 2019)

**Quelle:** <https://link.springer.com/article/10.1186/s12859-019-2819-0>

**Inhalt:** Linear-space, cache-effiziente Algorithmen für DL-Distanz. Bis zu
23× Speedup auf 24 Kernen (parallel). Cache-Miss-Reduktion durch Strip-Methode.

**Bewertung:** Diese Algorithmen berechnen die DL-Distanz zwischen zwei Strings
effizienter. Aber SymSpell nutzt bit-paralleles OSA (Hyyrö 2003), das bereits
O(n/64) ist und cache-freundlich arbeitet. Die Strip-Methode optimiert den
DP-Tabellen-Zugriff, aber bei Strings < 64 Zeichen (typisch für SymSpell) ist
der bit-parallele Ansatz bereits optimal. Kein messbarer Gewinn zu erwarten.
**Status:** nicht ausprobiert, aber geringe Erwartung (Strings zu kurz).

### 2.5 Pruning Radix Trie (Wolf Garbe, 2020)

**Quelle:** <https://seekstorm.com/blog/pruning-radix-trie>

**Inhalt:** Novel Radix-Trie mit Pruning für Auto-Complete. 1000× schneller als
ordinary Radix Trie.

**Relevanz für SymSpell (wörtlich):** „a Trie is a prefix tree, which requires a
common prefix. This makes it suitable for autocomplete or search suggestions, but
not applicable for spell checking. If your typing error is e.g. in the first letter,
than you have no common prefix, hence the Trie will not work for spelling
correction."

**Bewertung:** Explizit nicht anwendbar auf Spell-Correction. Nur für Auto-Complete.
Kein neuer Ansatz für SymSpell.

### 2.6 Embedding-based Query Spelling Correction (CEUR-WS, 2024)

**Quelle:** <https://ceur-ws.org/Vol-3689/WOWS_2024_paper_4.pdf>

**Inhalt:** Embedding-basierte Korrektur mit Word2Vec/GloVe. Vergleich mit
Hunspell, pyspellchecker, GPT-3.5.

**Bewertung:** ML-basiert, nicht algorithmisch. Latenz: Embedding-Lookup ~ms,
nicht µs. Für dieses Repo (mikrosekunden-Latenz, keine ML-Dependencies) nicht
relevant.

### 2.7 Contextual Multilingual Spellchecker (Adobe, 2023)

**Quelle:** <https://arxiv.org/html/2305.01082>

**Inhalt:** Low-Latency-Spellchecker für Adobe Express Queries. <1 ms Latenz
gegenüber 40+ ms für Aspell/Neuspell. Nutzt Finite-State-Automaten + Neuronale
Netze.

**Bewertung:** Spezialisiert auf kurze User-Queries, nutzt ML-Modelle. Nicht
anwendbar auf das SymSpell-Paradigma. Kein Speed-Gewinn für dieses Repo.

### 2.8 LMSpell (arXiv:2512.05414, 2026)

**Inhalt:** LLM-basierte Spell-Correction mit Pre-Trained Language Models.
Vergleich mit JamSpell, ASpell, Hunspell, SymSpell.

**Bewertung:** ML-basiert, nicht algorithmisch. SymSpell wird als Baseline genutzt,
nicht als Optimierungsziel. Kein neuer Speed-Ansatz.

### 2.9 Spelling Correction mit Caching (Indonesisch, 2026)

**Quelle:** <https://ejournal.pnc.ac.id/index.php/jinita/article/download/2893/1030/14195>

**Inhalt:** Caching von vorherigen Korrekturen + Cosine Similarity für Ranking.

**Bewertung:** Caching von Korrekturen ist identisch mit dem „Distanz-Cache"
Experiment in `OPTIMIZATIONS.md` § 14 (gescheitert: +1 µs Overhead). Kein
neuer Ansatz.

### 2.10 wordtree Crate (2026)

**Quelle:** <https://akesson.io/wordtree/>

**Inhalt:** Eine Datenstruktur, die Trie + Fuzzy Search + Auto-Complete kombiniert.
Benchmark: Exact Lookup 74 ns (vs. HashMap 8 ns), Spelling Correction 10× langsamer
als SymSpell, 3× größer als FST.

**Bewertung (wörtlich):** „Spelling correction is an order of magnitude slower
than symspell." Bestätigt, dass SymSpell bereits nahezu optimal ist.

### 2.11 A practical index for approximate dictionary matching (Cisłak & Grabowski, 2015)

**Quelle:** <https://arxiv.org/html/1501.04948v3>

**Inhalt:** Split Index basierend auf Dirichlet-Prinzip für Approximate Dictionary
Matching mit wenigen Mismatches. Cache-freundlich durch Datenkomprimierung.

**Bewertung:** Konkurrenz zu BK-Tree und SymSpell. Aber der Split Index ist für
Hamming-Distanz (Substitutionen) optimiert, nicht für Damerau-Levenshtein.
SymSpell's Delete-Index ist bereits ein spezialisierter Hash-Index, der für
Edit-Distanz optimiert ist. Kein Vorteil zu erwarten.

## 3. Zusammenfassung: Keine neuen Speed-Ideen

| Kategorie | Gefunden | Relevant? |
|---|---|---|
| Peer-reviewed SymSpell-Optimierung | **Keine** | — |
| Neue Datenstrukturen für Spell-Correction | Nein (Pruning Radix Trie = Auto-Complete only) | Nein |
| Cache-effiziente Edit-Distance | Zhao & Sahni 2019 (parallel, für lange Strings) | Marginal (Strings < 64) |
| SIMD für String-Matching | Nicht peer-reviewed für Spell-Correction | Offen (Vector API) |
| ML-basierte Correction | Viele (LLM, BERT, Embeddings) | Nein (Latenz, Dependencies) |
| Caching von Korrekturen | Indonesisch 2026 | Bereits gescheitert |
| BK-Tree Triangle Inequality | Wolf Garbe 2017 | Bereits gescheitert |
| Neue Hash-Funktionen | Nicht für Spell-Correction | Nein |

**Fazit:** Es gibt keine peer-reviewed Papers oder neuen Algorithmen, die
messbare Speed-Gewinne für SymSpell versprechen. Die Optimierungen, die SeekStorm
in Rust v7 umgesetzt hat (Bit-paralleles OSA, StringHashSet, Term-ID-Speicher,
SWAR-Parser), sind bereits in diesem Java-Repo implementiert. Die verbleibenden
Gewinne erfordern SIMD (Vector API, Preview) oder Cache-Line-Flattening — beide
unimplementiert und ungemessen.

## 4. Empfehlung

**Keine neuen Code-Änderungen.** Die Literaturrecherche bestätigt, dass das Repo
am Stand der Technik ist. Die einzigen verbleibenden ungemessenen Ideen sind:

1. **Vector API (SIMD)** — Preview, 2–7 % geschätzt
2. **Cache-Line-Flattening** — 2–4 % geschätzt, hoher Aufwand
3. **PGO via AOT-Cache** — 2–5 % geschätzt, geringer Aufwand

Details: `SPEED_OPTIMIZATION_OPPORTUNITIES.md`.

## 5. Messkommandos

```bash
# Baseline (stabil)
CP="symspell-lib/target/classes:symspell-lib/target/test-classes:$(cat /tmp/opencode/cp.txt)"
for i in 1 2 3 4 5; do
    java -cp "$CP" io.github.mightguy.spellcheck.symspell.benchmark.LookupPerf \
        frequency_dictionary_en_82_765.txt noisy_query_en_1000.txt 5 30000 2>&1 | grep TOP
done
```
