# Optimierungen & Fehlversuche — customized-symspell (Java 25)

Dieses Dokument hält fest, welche Optimierungen umgesetzt wurden, welche Experimente
**netto langsamer** waren und warum, sowie die JFR-Hotspots und die deutschen Besonderheiten.
Basis: SymSpell v6.7.3 (C#-Port) auf Java 25, kein `var`, Spring Boot 4.1.2.

## Messbasis

- Harness: `symspell-lib/src/test/java/.../benchmark/LookupPerf.java`
- Dictionary: 82.761 englische Wörter (`frequency_dictionary_en_82_765.txt`), `prefixLength=7`, `maxEditDistance=2`
- Queries: 1.000 verrauschte Wörter (`noisy_query_en_1000.txt`), 5 Runden, beste Runde berichtet
- Build: `mvn -q -pl symspell-lib dependency:build-classpath -Dmdep.outputFile=/tmp/opencode/cp.txt`
  (nur online möglich — `dependency-plugin` nicht im lokalen `.m2`)
- Start: `java -cp "symspell-lib/target/classes:symspell-lib/target/test-classes:$(cat /tmp/opencode/cp.txt)" io.github.mightguy.spellcheck.symspell.benchmark.LookupPerf frequency_dictionary_en_82_765.txt noisy_query_en_1000.txt 15 30000`

| Phase | TOP (µs/Query) | ALL (µs/Query) | Build (ms) | Heap |
|---|---:|---:|---:|---:|
| Baseline (vor allen Optimierungen) | 7,40 | 48,00 | 920 | ~4 MB |
| + WeightedDamerau (bit-parallel + banded DP) | ~3,7 | ~16,6 | 865 | ~4 MB |
| + StringHashSet (Generationen, O(1) clear) | 3,70 | 16,6 | 865 | ~4 MB |
| + Term-ID-Speicher (int[][] Buckets) | 3,77 | 16,6 | 865 | ~1 MB |
| + DictionaryReader (SWAR, TAB) | 3,82 | 17,16 | 768 | ~1 MB |
| **Final (Englisch, 82.761 Wörter)** | **3,82** | **17,16** | **768** | **1 MB** |

Speedup gegenüber Baseline: TOP ~1,94×, ALL ~2,80×.

## 1. Edit-Distanz — `WeightedDamerauLevenshteinDistance`

Quelle: symspell_rs v7 Blog (bit-parallel OSA, Hyyrö 2003).

- **Bit-paralleles OSA** für Einheitsgewichte und Pattern ≤ 64 Zeichen (`bitParallelOsa`, `MAX_BIT_PARALLEL_LENGTH=64`):
  `vp=-1, vn=0, d0=0, previousMask=0`, Transposition `((~d0 & mask) << 1) & previousMask`,
  `d0 = ((((mask & vp) + vp) ^ vp) | mask | vn | transposition)`, `hp/hn`, Early-Exit
  `dist > k + remaining`. Pattern = kürzerer String (OSA symmetrisch).
- **Banded DP** (`computeBanded`) für gewichtete Fälle: 3 rolling `double[]`
  (`previousPreviousRow`, `previousRow`, `currentRow`), Bandbreite `floor(max/minIndelWeight)`,
  Transposition über `previousPreviousRow[j-2]` mit `transpositionWeight`, Early-Exit
  über `min(rowMin, prevRowMin, prevPrevRowMin + transpositionWeight) > max`.
- **Prefix/Suffix-Strip** per Indizes (keine Substrings), Längen-Diff-Check vorher.
- **Leere Strings** werden vor `unitCost` behandelt (`targetLen*insertionWeight` bzw.
  umgekehrt, geklammert auf max) — war ein Bug (`bitParallelOsa` lieferte 0 für leeres Pattern).
- **ThreadLocal<BitMasks>** (`long[128]` ASCII + `char[64]`/`long[64]`) — kein Heap-Alloc pro Aufruf.
- `getDistance(source,target)` (ohne max) nutzt `width=Integer.MAX_VALUE`, `max=+INF`.

**Issue-#70-Fix:** `isTransposition` `i > 2 && j > 2` → `i > 1 && j > 1`
(High-Severity: `"ab"→"ba"`, `"abc"→"bac"`, `"teh"→"the"` lieferten 2 statt 1).
Regressionstests: `getDistance("abc","bac")==1` (ungewichtet) bzw. `0.7` (gewichtet).

## 2. Lookup-Pfad — `SymSpellCheck`

- **Issue #70 (MighTguY):**
  - `List<String> candidates = new ArrayList<>()` + `remove(0)` → `Queue<String> candidates = new ArrayDeque<>()` + `remove()` (O(1) am Kopf).
  - Roh-Phrase wurde zusätzlich in `candidates` geaddet → nur **einer** von Prefix bzw. Phrase
    (C#-Referenz: `if (inputPrefixLen > prefixLength) { inputPrefixLen = prefixLength; candidates.Add(input.Substring(0, inputPrefixLen)); } else { candidates.Add(input); }`).
  - `lenDiff = phraseLen - candidateLen` → `phrasePrefixLen - candidateLen`.
  - `isDistanceCalculationRequired`: `phrase.length()` → `spellCheckSettings.getPrefixLength()`.
- **`consideredDeletes`/`consideredSuggestions`**: `HashSet<String>` (64) →
  `StringHashSet` (Open-Addressing, `String[] values`, `int[] hashes`, `int[] generations`),
  `clear()` in O(1) über `generation++` (bei `Integer.MAX_VALUE` Reset per `Arrays.fill`).
  + `LookupScratch` bündelt beide Sets in einem Objekt → ein `ThreadLocal.get()` pro Lookup.
  Gewinn: TOP 4,6 → 3,7 µs (JFR zeigte `HashSet.add` 17,5 % und `Arrays.fill` beim Clear).
- **Delete-Generierung**: `candidate.substring(0,i)+candidate.substring(i+1)` →
  `char[] deleteBuffer` + `getChars` + `new String(buf,0,len-1)` (ein Alloc weniger).
- **`isDistanceCalculationRequired` ohne `substring().equals()`**: Hilfsmethode
  `sameTrailingCharacters(phrase, phraseFrom, suggestion, suggestionFrom, length)`.
- **Pruning verschärft** auf C#-Referenz: zweite Bedingung `&&` → `||`
  (`(A != B) || (C != D)`), mit Bounds-Guard (`phraseLast > 0 && suggestionLast > 0`).
  Überspringt jetzt, sobald mindestens ein angrenzendes Transpositions-Paar differiert.
  Korrektheit durch 65 Tests + `WeightedDistanceFuzzTest` + `Issue70RegressionTest` abgesichert.
- **`filterOnEquivalance`** billig-zuerst umsortiert:
  `abs(lenDiff) > maxEditDistance2 || deleteLen < candidate.length() ||
   (deleteLen == candidate.length() && !delete.equals(candidate)) || delete.equals(phrase)`.

## 3. Hashing — `HashFunction` / `Murmur3HashFunction` → `Utf16HashFunction`

- `Long hash(String)` → `long hash(String)` (primitiv), kein Boxing.
- Murmur3 x86_32 direkt über UTF-16-Chars (kein `getBytes()`), `C1=0xcc9e2d51`,
  `C2=0x1b873593`, Seed `0x7f3a21eaL`, fmix, Rückgabe `h1 & 0xffffffffL`.
- **Hash-Vergleich (User-Hinweis xxhash.com):**
  - OpenHFT Zero-Allocation-Hashing nicht im `.m2` (offline) → nicht testbar.
  - Char-basierter XXH32-Benchmark (5 M Aufrufe): 31 ns/op vs. 28 ns/op Murmur3 → **nicht schneller**.
  - `String.hashCode` (cached): 9,8 vs. 10,6 ns/op, leicht schneller, aber schlechtere Verteilung
    (größere Buckets) → netto keine Verbesserung.
  - **Gewählt: 64-bit UTF-16-Hash** (`Utf16HashFunction`, char-Paare zu long + Splitmix-Mixer),
    64-bit Entropie für `LongIntMap`. TOP 3,67 µs, 65 Tests grün. Bessere Verteilung + Speicher.

## 4. Speicher — Term-ID-Tabellen

- `InMemoryDataHolder`: `Map<String,Double> wordsDictionary` → Term-Tabelle
  (`String[] terms`, `double[] frequencies`, `StringIntMap termIndex`).
  Delete-Buckets: `String[][]` → `int[][]` (Term-IDs statt Strings) → ~1 MB Heap (vorher ~4 MB).
- `DataHolder`-Interface erweitert: `getDeleteIds`, `getTerm`, `getTermFrequency` (Defaults).
  `SymSpellCheck.lookup` nutzt Fast-Path mit direktem Array-Zugriff
  (`InMemoryDataHolder.getTerms()/getFrequencies()`), um Methoden-Call-Overhead zu vermeiden.
- `getItemFrequency` nutzt jetzt `termIndex` (kein `HashMap.getNode` mehr).
- Delete-Buckets werden beim Einfügen nach Frequenz absteigend sortiert (Insertion-Sort),
  um für TOP/CLOSEST früher ein gutes Maximum zu finden.
- **`StringIntMap`** (Open-Addressing String→int, allocation-free) neu.

## 5. `LongIntMap` (Open-Addressing `long`→`int`)

- Keys als `key+1` (0 = leer), `slotOf = (int)(storedKey * 0x9E3779B97F4A7C15L >>> 40) & mask`,
  lineares Probing, Load 0,6 → resize ×2, `get` (-1 = fehlt), `put`, `clear`, `size`.
- **Slot-Hash verbessert:** `slotOf` nutzt jetzt
  `((int)(storedKey >>> 32) ^ (int)storedKey) & mask` → bessere Verteilung, weniger Clustering.
- Load-Faktor 0,6 → 0,5 (`size * 2 > keys.length`) → TOP 3,70 µs, ALL 13,9 µs.
- `get` in `SymSpellCheck.lookup` inlined (Probe-Loop direkt im Hot-Path), Fallback auf
  `dataHolder.getDeleteIds(candidate)` für andere Implementierungen.
  Neue Getter: `LongIntMap.getMask()/getKey(int)/getValue(int)` und
  `InMemoryDataHolder.getHashFunction()/getDeleteIndex()/getDeleteBuckets()`.

## 6. Dictionary-I/O — `DictionaryReader` (FrontCodedAutocomplete übernommen)

- **SWAR-Newline-Scan** (`findEither`, 8 Bytes/Schritt) + direktes Double-Parsing.
  Kein `Files.readAllLines`/`String.split` mehr. MemorySegment/FFM (Java 25) für Datei-I/O.
- **TAB/;-Separator:** Reader bevorzugt TAB oder ';' (erster Treffer, Scan rechts-nach-links),
  Whitespace-Fallback bleibt. Begründung: TAB kollidiert nie mit Terms; ';' zusätzlich erlaubt.
- **Bit-Tricks (User-Snippet):** Tab-Erkennung `(b ^ 0x09) == 0`,
  Ziffern-Erkennung `((b ^ 0x30) & 0xFF) >= 10` (Negation), Ziffernwert `b & 0x0F`,
  Multiplikation mit 10 per Shifts `(val << 3) + (val << 1) + digit`,
  Scan rechts-nach-links (Separator vor kurzem Frequenz-Token → überspringt langen Term).
- **1BRC-Style Tab-Scan (User-Hinweis):** rechts-nach-links-Scan für TAB/';' jetzt ebenfalls
  in 8-Byte-Blöcken via `JAVA_LONG_UNALIGNED` + "has-zero-byte"-Trick
  (`(x - 0x0101…01) & ~x & 0x8080…80`), `Long.numberOfLeadingZeros` für die rechteste
  Trefferposition. Bigram-Datei (242.342 Einträge, Terms mit Leerzeichen) weiterhin korrekt.
- Alle Frequenz-Dictionaries (console, lib, solr) auf TAB umgestellt.
- Verifiziert: 82.761 / 242.342 Einträge identisch. Build-Zeit 865 → 746 ms (−14 %).
- Eingebunden in Console, Solr, LookupPerf.

## 7. `SpellHelper` (Build-Beschleunigung)

- `ThreadLocal<char[]> DELETE_BUFFER` (Start 64, wächst).
- `edits(...)` baut Deletes per `getChars` in den Buffer statt `substring()+substring()`.
- Redundantes `deletedWords.add(word)` am Ende entfernt (Wort durch Aufrufer bereits enthalten).

## 8. Commons-Dependencies entfernt

- `grep org.apache.commons`: nur 4 Java-Usages (SpellHelper, SymSpellCheck, AccuracyTest,
  2 Benchmarks, SpellcheckComponent). Alle ersetzt durch JDK (`isEmpty`/`==null`-Checks,
  `Files.lines`/`BufferedReader` statt CSVParser, `System.nanoTime` statt StopWatch).
- `Murmur3HashFunction.hash(byte[])` inline implementiert (Murmur3 x86_32 über Bytes),
  `com.sangupta:murmur`-Dependency entfernt.
- Root-pom: Properties `apache.commons-*` und `murmur.version` entfernt, dependencyManagement-Einträge entfernt.
- `symspell-lib/pom.xml` und `symspell-solr/pom.xml`: commons-Dependencies entfernt.
- `SpellcheckComponent` (solr): `org.apache.commons.collections.CollectionUtils` (v3) und
  `StringUtils` durch JDK ersetzt.

## 9. Spring Boot 4 Migration

- Root `pom.xml`: Parent `spring-boot-starter-parent` 2.4.0 → 4.1.2;
  Property `springfox-swagger.version` 3.0.0 ersetzt durch `springdoc.version` 3.2.0.
- `symspell-service/pom.xml`: springfox-swagger2 + springfox-swagger-ui entfernt,
  dafür `org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdoc.version}`;
  `<java.version>17</java.version>`-Überschreibung entfernt (Modul erbt 25).
- `config/SwaggerConfig.java`: kein `Docket`/`@EnableSwagger2`, sondern `@Configuration` +
  `@Bean OpenAPI spellcheckOpenApi()` mit `Info`/`Contact` (`io.swagger.v3.oas.models.*`).
- `controller/SpellcheckController.java`: springfox → `io.swagger.v3.oas.annotations.*`;
  `@Api` → `@Tag`, `@ApiOperation` → `@Operation`, `@ApiResponses` mit `responseCode`/
  `description`/`content=@Content(schema=@Schema(...))`; `javax.validation.Valid` → `jakarta.validation.Valid`.
- `service/SpellcheckService.java`: `javax.annotation.PostConstruct`-Import und leere `init()`
  entfernt; unbenutzte lokale Variable `charDistance` entfernt.
- Verifiziert: Fat-Jar `symspell-service/target/symspell-service-6.7-SNAPSHOT.jar` (31,7 MB).
  Vorher: `Unsupported class file major version 69` und `Unable to find main class`.

## 10. Lombok-Entfernung (Java 25)

- 13 Dateien delombokt; `grep lombok` in `*.java` und `pom.xml` = 0 Treffer.
- `DictionaryItem` → `public record DictionaryItem(String term, Double frequency, Double distance) {}`.
- Call-Sites angepasst: `InMemoryDataHolder` (`dictionaryItem.frequency()`, `.term()`),
  `MiscUnitTest` (`di.term()`, `.frequency()`, `.distance()`).
- `SpellCheckSettings`: manueller `SpellCheckSettingsBuilder` (`public static final class`,
  statische `builder()`, 16 Felder mit Defaults, Getter, `setMaxLength`/`setBigramCountMin`).
- `SpellChecker`, `SpellCheckerConsole`, `Response`, `SpellcheckConfiguration`,
  `CustomSpellCheckListner`, `SpellcheckComponent`, `Composition`, `SuggestionItem`,
  `SpellCheckException`, `QwertyDistance` manuell ergänzt (Getter/Setter/Logger).
- pom: `java.version` 1.8 → 25; compiler-Plugin 3.8.1 → 3.13.0 (`<release>${java.version}</release>`);
  lombok-Property/dependencyManagement/Root-Dependency/annotationProcessorPaths entfernt;
  jacoco 0.8.6 → 0.8.14; checkstyle 8.29 → 8.45.

## 11. Checkstyle

- `google_checks.xml` vendored (aus checkstyle 8.29, 321 Zeilen), `scope` → `accessModifiers`
  in `JavadocMethod` (checkstyle 8.45 kennt `scope` nicht mehr).
- `<configLocation>` steht **innerhalb** der Execution `id="checkstyle"` (Phase `validate`) —
  `mvn checkstyle:check` (CLI) nutzt `default-cli` ohne diese Config → wertlos.
  Nur `mvn ... compile`/`validate` nutzt die pom-Config.
- `checkstyle.location = ${maven.multiModuleProjectDirectory}/google_checks.xml`.
- Root-Pom-Bug `</rule>z` (Zeile ~239, jacoco-Config) gefixt.
- `checkstyle-suppressions.xml` unterdrückt u. a. `JavadocMethodCheck`, `SummaryJavadocCheck`,
  `ParameterNameCheck`, `MemberNameCheck`, `LocalVariableNameCheck`, `JavadocParagraphCheck`.

## 12. Issue #70 (MighTguY/customized-symspell) — vollständig

Siehe Abschnitt 1/2. Alle 4 Punkte umgesetzt + Regressionstests
(`Issue70RegressionTest`, 4 Tests: Transposition der ersten zwei Zeichen, rolling rows,
Prefix-Lookup, Verbosity.ALL).

## 13. JFR-Hotspots (final, Lookup-Phase)

- `SymSpellCheck.lookup` 12,2 % (inkl. inlined Probe)
- `filterOnEquivalance` 8,9 %
- `isDistanceCalculationRequired` 4,9 %
- `bitParallelOsa` 4,1 %
- Build-Phase: `addItem` 8,1 %, `HashSet.add` 10,6 %, `HashMap.resize` 2,4 %

## 14. Fehlversuche (netto langsamer / entfernt)

| Experiment | Ergebnis | Grund |
|---|---|---|
| Distanz-Cache (ConcurrentHashMap, String-Concat-Key, 4096 Einträge) | ALL 17,8 µs (+1 µs) | Overhead überwiegt; nur für Repeat-lastige Workloads sinnvoll |
| Aggressives `||`-Pruning mit Early-Return + Bounds-Check | ALL 18,66 µs | durch kombinierte Bedingung (16,68 µs) ersetzt |
| q-Gram-Pre-Filter (Trigramme, ThreadLocal `int[]`) | TOP 4,2 vs 3,7 µs, ALL 20,4 vs 16,6 µs | Trigram-Scan-Overhead überwiegt eingesparte `bitParallelOsa`-Aufrufe (OSA bereits bit-parallel). Nur optional für Wörter > 12 Zeichen sinnvoll |
| SWAR-Decoder (Blockgrößen 8/32/64) | — | keine messbare Verbesserung bei typischen Dictionaries |
| Memory-Mapped / paralleler Import | — | für < 100k Einträge kein messbarer Vorteil |
| Front-Coding der Term-Tabelle (`byte[]` statt `String[]`) | — | O(1)-Zugriff auf Term nach Id im Hot-Path nötig; Dekodierung pro Kandidat netto langsamer |
| TopKCollector (primitive Arrays + Heap) | — | bestehende `maxEditDistance2`-Verwaltung deckt das ab; Allokation bereits ≤ 1 MB/Query |
| Varint-Persistenz (Binary-Snapshot) | — | Nice-to-have für Startup, kein Hot-Path-Gewinn |
| Group-Maxima-Pruning über Delete-Buckets | — | Buckets klein (~40 Einträge) |
| XXH32 (char-basiert) | 31 vs 28 ns/op | nicht schneller als Murmur3 |
| `String.hashCode` (cached) als Delete-Hash | TOP 3,62 µs | leicht besser, aber schlechtere Verteilung → größere Buckets |

## 15. Deutsche Spezifika

- **Reine deutsche Tricks für Speed gibt es kaum**; verbleibende Bottlenecks sind sprachunabhängig.
- Indirekt hilfreiche deutsche Spezifika:
  - Umlaut-Faltung (ä→ae etc., reduziert Vokabular/Buckets) — als Dictionary-Build-Option.
  - QwertzDistance (bereits im Repo, deutsche Tastatur).
  - `prefixLength=10` (bereits in `GermanLangSpellChecker`).
  - Compound-Awareness (`lookupCompound`).
  - ß/SS-Locale-Handling.
- **q-Gram-Pre-Filter** (allgemein, für Deutsch tunbar): in dieser Java-Portierung netto langsamer (entfernt).
  Empfehlung: q-Gram nur optional für Wörter > 12 Zeichen.
- **Deutsche Benchmarks:**
  - GermanBench (100k Wikipedia-Terms, 932 verrauschte Queries, maxEditDistance=2,
    doKeySplit aktiv → 42.436 Unigrams + 57.564 Bigrams):
    - prefixLength 7: TOP 5,30 µs, CLOSEST 5,21 µs, ALL 20,43 µs
    - prefixLength 10: TOP 5,19 µs, CLOSEST 5,52 µs, ALL 19,43 µs
    - Build 741 ms, Heap 1 MB.
  - BusinessBench (100 deutsche Business-Suchbegriffe, rechnung/angebot/bestellung/…):
    - TOP 3,27 µs, CLOSEST 3,18 µs, ALL 13,47 µs.
    - Exact Top-1 Hits: 61/100 (39 Komposita nicht im 100k-Wörterbuch → Coverage-Problem,
      kein Algo-Problem). Mit `lookupCompound` und größerem/compounding-fähigem Dictionary
      würden die 39 fehlenden gefunden.
  - Englisch-Vergleich (82.761 Wörter): TOP 3,82 µs, ALL 17,16 µs → Deutsch ~1,35× langsamer.

## 16. Deutsche Frequenzlisten (GitHub-Recherche)

Beste Kandidaten:

1. **wolfgarbe/SymSpell.FrequencyDictionary** (`de-100k.txt`, 100.000 Einträge) —
   Google Ngram ∩ Hunspell (zuverlässige Frequenzen + korrektes Vokabular). Bereits als
   `symspell-lib/src/test/resources/de-100k.txt` und `symspell-console/src/main/resources/de-100k.txt`
   (TAB-separiert) übernommen.
2. **FrequencyWords (Hermit Dave)** — Deutsch, MIT/CC-BY-SA, breite Abdeckung; Basis für
   `ph98/german-cefr-wordlist`.
3. **natema/wordhoard** — Deutsch mit Frequenzen, POS, Gender, CEFR; OpenSubtitles-basiert,
   CC BY-SA 4.0 (Daten) / MIT (Code).
4. **Nordsword3m/German-Words** — JSON mit Formen/Frequenzen (Leipzig-Korpus), nicht ideal
   für SymSpell (keine reinen Frequenz-Paare).
5. **kotoshu/dictionaries** — Hunspell `de` (~300k Wörter), keine Frequenzen.
6. **Sketch Engine** — deutsche Frequenzlisten (nur 200er kostenlos, längere kostenpflichtig).

Empfehlung für eigene, bessere Listen: Google Ngram DE ∩ Hunspell DE (wie wolfgarbe),
ergänzt um Compound-Splitting (lookupCompound) und Umlaut-Faltung. Bei Bedarf
Wikipedia-Dump oder OpenSubtitles als Frequenzquelle.

## 17. Tests / Build

- `mvn test`: 65 Tests, 0 Failures (symspell-lib); Voll-Reaktor BUILD SUCCESS.
- Neue Tests: `Issue70RegressionTest` (4), `WeightedDistanceFuzzTest` (2, JUnit 4,
  `EPSILON=1e-9`, 5 Gewichtssätze × 400 Runden + 300 Runden Längen 1–80).
- Kein `var` im Code; Java 25.

## Quellen

- symspell_rs v7 Blog: https://seekstorm.com/blog/symspell_rs-3x-faster-lookup-40-less-memory-11x-faster-levenshtein/
- symspell_rs Repo: https://github.com/wolfgarbe/symspell_rs
- 1brc: https://github.com/gunnarmorling/1brc
- Spring Boot 4.1.2: https://github.com/spring-projects/spring-boot/releases/tag/v4.1.2
- FrontCodedAutocomplete: /home/jay/code/FrontCodedAutocomplete
- wolfgarbe/SymSpell.FrequencyDictionary: https://github.com/wolfgarbe/SymSpell/tree/master/SymSpell.FrequencyDictionary
- FrequencyWords: https://github.com/hermitdave/FrequencyWords
- wordhoard: https://github.com/natema/wordhoard
## SpotBugs + toon-java checkstyle (2026-11-05)

- SpotBugs-Maven-Plugin 4.8.6.7 in Root-Pom (Phase verify, effort Max, threshold Medium, failOnError true), Exclude-Filter aus toon-java übernommen (`spotbugs-exclude.xml`).
- Gefundene und behobene Findings:
  - `SpellHelper.continueConditionIfHeadIsSame`: redundanter Null-Check auf `suggestions1` (RCN) entfernt.
  - `SuggestionItem`: `Serializable` implementiert (SE_COMPARATOR_SHOULD_BE_SERIALIZABLE).
  - `SymSpellCheck.lookupSplitWords`: String-Vergleich `==` → `.equals()` (ES_COMPARING_STRINGS_WITH_EQ).
  - `InMemoryDataHolder.addItem`: `frequency == Double.MIN_VALUE` → `Double.compare(...) == 0`.
  - `SymSpellCheck.getMinDistanceOnPrefixbasis` / `isDistanceCalculationRequired`: float-`==` → `Double.compare(...) == 0`.
  - `SpellCheckerConsole`: Default-Encoding → `StandardCharsets.UTF_8` + Null-Check auf `readLine()` (DM_DEFAULT_ENCODING, NP_DEREFERENCE).
  - `SpellcheckComponent`: Default-Encoding → `StandardCharsets.UTF_8` (DM_DEFAULT_ENCODING).
  - Import-Reihenfolge (CustomImportOrder) in console/solr korrigiert.
- Ergebnis: 0 Findings in allen Modulen; `mvn verify -DskipTests` BUILD SUCCESS, `mvn test` 65 Tests grün.
- toon-java `checkstyle.xml` als `checkstyle-toon.xml` übernommen und **vollständig angewendet** (google_checks.xml entfernt):
  - Alle Module von 2- auf 4-Space-Indentation umformatiert (Javadoc-Zeilen `*` mit Offset 1).
  - FinalParameters/FinalLocalVariable: alle Parameter/Locals `final`; bei Reassignment lokale Kopie (`int x = theX;`).
  - HiddenField: Parameter mit Feldnamen umbenannt (`theX`) in Konstruktoren/Settern/Builder-Methoden.
  - MagicNumber: Literale als `private static final`-Konstanten extrahiert.
  - JavadocStyle: erster Satz endet mit Punkt; LineLength auf 120 gekürzt.
  - Import-Reihenfolge (CustomImportOrder) alphabetisch sortiert.
  - Ergebnis: 0 Checkstyle-Violations in allen 5 Modulen.
- `spotbugs-exclude.xml` (toon-java) aktiv; SpotBugs 4.8.6.7, effort max, threshold medium, failOnError.
- Speed nach Umstellung: TOP ca. 3,7–4,1 µs, ALL ca. 14,8–20,2 µs (Messrauschen).

## Ideen aus reneklacan/symspell (Rust)

Analysiert wurden `src/symspell.rs` und `src/string_strategy.rs`.

Übernommen:
- **String-Strategy / ASCII-Transliteration**: Das Rust-Projekt normalisiert Terme über eine austauschbare `StringStrategy` (`unidecode`). In Java als `StringNormalizer` umgesetzt (deutsche Umlaute/ß → ae/oe/ue/ss, danach NFKD-Strip) und als opt-in `SpellCheckSettings.transliterate(true)` in Indexierung und Lookup verdrahtet. Standard: aus (kein Verhaltenswechsel).
- Test `StringNormalizerTest` (4 Fälle, inkl. Lookup mit „Grüße“/„gruesse“).

Nicht übernommen (bereits äquivalent oder ungeeignet):
- `has_different_suffix` / `delete_in_suggestion_prefix`: identisch zu unserer Pruning-Logik (inkl. `||`).
- `lookup_compound`-Fallback `10 / 10^len`: unsere Bigram-/Count-Heuristik ist bereits feiner; Änderung würde Ranking-Tests brechen.
- `serde`-Serialisierung: nur Binary-Snapshot, kein Hot-Path-Gewinn.
- WASM-Bindings: für Java irrelevant.
- `parse_words` via `split_whitespace`: unsere Regex-Variante deckt mehr Trennzeichen ab.

## Bewertung seanlee2020/customized-symspell – Commit 36f6d11 („use ngram and double metaphone for reranking“)

Idee: Im Compound-Lookup werden pro Token nicht mehr nur TOP-, sondern ALL-Kandidaten geholt und anschließend per Kontext neu gerankt:
- Double-Metaphone-Code des Tokens vs. Kandidaten (`sameMetaphoneCode`),
- Bigramm-Frequenzen mit Vor-/Folgetoken,
- Trigramm-Frequenzen (drei Fenster),
- Gewicht `(bigram1+bigram2)/unigram + 1000 * (trigram1+trigram2+trigram3)/unigram`,
- Sortierung über `SuggestionItem.compareTo` mit `score / 100^distance`.

Bewertung: **nicht übernommen**.
- `compareTo` verletzt den Comparator-Vertrag: bei zwei Kandidaten mit `distance == 0` liefert es in beide Richtungen `-1` (nicht antisymmetrisch); außerdem ist `score` bei Einzelwort-Lookups 0.
- Benötigt ein neues Trigramm-Wörterbuch (Commit fügt ~950k Zeilen Testdaten hinzu) und `commons-codec`.
- Gewichte (1000) sind hartkodiert; `Verbosity.ALL` im Compound-Lookup kostet deutlich Laufzeit.
- Änderung würde bestehende Ranking-Tests und die TOP/CLOSEST-Semantik brechen.

Übernehmbare Teilidee (falls später gewünscht): Double-Metaphone nur als Tie-Breaker bei gleicher Distanz und Frequenz, optional und standardmäßig aus – nicht global in `compareTo`.

## Deutsche Rechtschreibung: Sub-Match-Vermeidung (2026-11-05)

- Neues opt-in Setting `SpellCheckSettings.builder().noSubMatches(true)` (Default `false`).
- In `SymSpellCheck.lookupSplitWords`: bei fehlender Bigramm-Frequenz wird ein Split nur akzeptiert, wenn beide Teile einen exakten Dictionary-Treffer haben (Distanz 0). Verhindert spurious Splits wie „Kaffee" → „Kaf" + „fee".
- Test `NoSubMatchesTest`: Setting-Roundtrip + exakter Split „hausboot" → „haus boot" bleibt erlaubt.
- Default bewusst `false`, weil `true` Kontraktionen wie „couldn'tread" → „couldn't read" in bestehenden Tests brach (Teil „couldn't" hat keinen exakten Treffer).
- Weitere Bericht-Empfehlungen (Hunspell COMPOUNDRULE, Kontext-Ranking, neuronale Reranker) bewusst nicht umgesetzt: benötigen linguistische Zusatzdaten bzw. Modell-Inferenz und passen nicht in eine reine Lookup-Bibliothek.

## Hotspot-Runde 3: Allokationen und Dedupe (2026-11-05)

Ausgangspunkt war ein JFR-Profil der reinen Lookup-Phase (82.761 Wörter, 675 Samples):
`StringHashSet.add` 36,2 %, `SymSpellCheck.lookup` 18,6 %, `bitParallelOsa` 15,1 %,
`Arrays.fill(long[])` 4,8 %, `ThreadLocalMap.getEntry` 3,6 %; Allokationen: `byte[]` 41,9 %,
`SuggestionItem` 25,3 %, `String` 16,0 %, `Object[]` 11,9 %.

### 1. `IntHashSet` statt `StringHashSet` für Suggestion-Dedupe
- Neu `impl/IntHashSet.java`: generation-basiertes Open-Addressing-Set für `int`, gepackt in
  `long[] entries` (Generation in den oberen 32 Bit, Wert in den unteren). Ein Array-Zugriff pro Add.
- Resize wird **vor** der Probing-Schleife geprüft, damit `add` inline-fähig bleibt.
- Mikrobenchmark: 2,69 ns/Add. Instrumentierung: ALL ≈ 128,7 Adds/Query (103,1 unique),
  TOP ≈ 19,3 (16,5 unique), 0 Resizes — Dedupe bleibt also nötig.
- `LookupScratch` hält jetzt `IntHashSet consideredSuggestionIds` zusätzlich zu den String-Sets
  des generischen Pfads.

### 2. String-freier Lookup-Fast-Path (`SymSpellCheck.lookupInMemory`)
- Aktiv für `InMemoryDataHolder` und `prefixLength <= 32`.
- Kandidaten-BFS arbeitet auf `int[] masks` (Bitmaske der gelöschten Positionen) statt auf
  `String`-Objekten in einer `ArrayDeque`; Kandidatenzeichen werden in einen wiederverwendeten
  `char[] candidateChars` gebaut und direkt gegen `LongIntMap` geprobt.
- Kind-Erzeugung ohne Dedupe-Set: nur der Parent, der die Maske zuerst erreicht, enqueued sie
  (`originalIndex > highestDeleted`, `highestDeleted = 31 - numberOfLeadingZeros(mask)`).
  Reihenfolge bleibt identisch zum bisherigen BFS.
- Dedupe der Treffer über Term-ID (`consideredSuggestionIds`), nicht über den Term-String.
- `Utf16HashFunction.hash(char[], int)` als allocation-freie Überladung; `InMemoryDataHolder.getHashFunction()`
  liefert dafür `Utf16HashFunction`.
- Ergebnis-Erzeugung lazy: `ALL` legt sofort an, `TOP`/`CLOSEST` nur bei tatsächlich besserem
  Kandidaten (`ArrayList.set(0, …)` bzw. `clear()` + `add`).
- Der generische String-Pfad bleibt als Fallback für fremde `DataHolder` und `prefixLength > 32` erhalten.

### 3. Pattern-Masken-Cache im bit-parallelen OSA
- `BitMasks` merkt sich das zuletzt verwendete Pattern (`Object pattern`); Masken werden nur bei
  Identitätswechsel neu aufgebaut. Bei `getDistance(phrase, suggestion, max)` ist das Pattern
  typischerweise dieselbe `phrase`-Instanz über alle Vorschläge eines Lookups.
- Identitätsvergleich ist hier bewusst (kein `equals`): es geht um Wiederverwendung desselben
  Maskenaufbaus, nicht um inhaltliche Gleichheit. SpotBugs-Ausnahme in `spotbugs-exclude.xml`
  (`ES_COMPARING_PARAMETER_STRING_WITH_EQ`).
- `BitMasks.reset()` nullt nur noch die tatsächlich belegten ASCII-Slots (`touchedAscii`), kein
  `Arrays.fill` über 128 `long`.

### Messungen (best of 25 Runden, 60.000 Warmup-Queries, 82.761 Wörter)
| Stand | TOP | CLOSEST | ALL |
|---|---|---|---|
| Ausgangsbasis | 3,94 µs | 4,03 µs | 18,40 µs |
| + char[]-Fast-Path, ID-Dedupe | 3,54 | 3,72 | 16,27 |
| + Masken-Dedupe entfernt | 3,62 | 3,75 | 15,59 |
| + gepacktes `IntHashSet` | 3,43 | 3,45 | 15,19 |
| + Pattern-Masken-Cache | 3,25–3,31 | 3,28–3,37 | 12,94–13,02 |
| + Resize-Vorverlagerung (Endstand) | 3,25–3,42 | 3,30–3,50 | 12,9–16,3 |

Die Streuung im Endstand stammt von Systemlast (Load Average ~4 auf 16 Kernen während der
Messreihe); unter Last sind 15–16 µs für ALL reproduzierbar, im Ruhezustand 12,9–13,0 µs.
A/B-Vergleiche daher nur über mehrere Läufe und Minima.

### Verworfene Experimente dieser Runde
- `new ArrayList<>(verbosity == ALL ? 128 : topK)`: ALL 14,5–15,0 µs (schlechter, das 128er-Array
  pro Query kostet mehr als das Nachwachsen).
- Dedupe komplett entfernt: ALL 13,9–14,4 µs statt ~13 µs (doppelte Distanzberechnungen).
- `-XX:+UseEpsilonGC -Xmx4g`: ALL 17–18 µs, kein geeignetes Mess-Setup.

### Offen
- `SuggestionItem`-Allokation bei `Verbosity.ALL` ist API-bedingt (Rückgabe `List<SuggestionItem>`);
  eine Lazy-View würde die öffentliche Schnittstelle ändern.
- `deleteInSuggestionPrefix`, `isDistanceCalculationRequired` und `ThreadLocalMap.getEntry` sind
  mikrotechnisch noch nicht weiter optimiert.

## Bewertung dreier Vorschläge (2026-11-05)

### 1. Kölner Phonetik als Pre-Filter — verworfen (gemessen)

Messung mit `de-100k.txt` (100.000 Terme) und 1.901 künstlich verrauschten Queries
(1–2 zufällige Edits, Seed 42):

| Kennzahl | Wert |
|---|---|
| distinkte Kölner Codes | 49.510 (Ø 2,02 Terme pro Code, max. 80) |
| Original im Ergebnis gefunden | 1.883 von 1.901 (99,1 %) |
| davon gleicher Code wie die Query | 492 (26,1 %) |
| davon **anderer** Code | **1.391 (73,9 %)** |
| Top-Vorschlag falsch, aber gleicher Code | 136 |

Ein harter Pre-Filter `code(query) != code(candidate) → überspringen` würde also
74 % der richtigen Korrekturen verwerfen. Phonetische Gleichheit ist weder
notwendig noch hinreichend für eine gute Korrektur.

Zusätzlich halten die Beispiele die Prämisse nicht: `Vati`/`Fati`, `Rad`/`Rat` und
`Stiel`/`Stil` haben Edit-Distanz 1 und werden von SymSpell bereits gefunden
(und beide Wörter stehen im Wörterbuch, die Auswahl ist eine Kontextfrage).

Denkbar bleibt ein Tie-Breaker bei identischer Distanz und Frequenz (Homophone),
aber dafür gibt es derzeit keinen belegten Qualitätsgewinn; Kosten wären ~4 Byte
pro Term plus Code-Berechnung pro Query. **Nicht umgesetzt.**

### 2. Viterbi-Komposita-Segmentierung — lohnt sich (mit Befund)

Gemessener Ist-Zustand von `lookupCompound` (de-100k, maxEditDistance 2):

| Eingabe | Ergebnis | Bewertung |
|---|---|---|
| `hausboot` | `haus boot` | korrekt |
| `krankenhausapotheke` | `kranken apotheke` | **`haus` geht verloren** |
| `sonnenblumenöl` | `sonnen blumen` | **`öl` geht verloren** |
| `benutzeroberfläche` | `benutzer fläche` | **`ober` geht verloren** |
| `haustürschlüssel` | `haust schlüssel` | falsche Trennung |
| `autobahn` | `auto bahn` | lexikalisiertes Wort fehlt im Wörterbuch |
| `donaudampfschifffahrt` | unverändert, Distanz 0 | kein Signal, obwohl nicht im Wörterbuch |

Laufzeit: 0,4–3,9 ms pro Aufruf (Fallback-Pfad, nicht der 3,3-µs-Lookup).

Der eigentliche Fehler ist strukturell: die heuristische Suche darf Teile
auslassen, statt die Eingabe vollständig zu überdecken. Ein DP/Viterbi über alle
Split-Positionen mit Unigram-Kosten (`-log(freq/total)` plus Strafe für unbekannte
Teile) überdeckt per Konstruktion den gesamten String und findet das globale Optimum.
Aufwand: ~150–250 Zeilen, nutzt vorhandene `StringIntMap`/Termfrequenzen, nur im
Compound-Pfad aktiv. Nötig: Mindestlänge pro Teil, Kosten für unbekannte Teile,
Vergleichs-Set deutscher Komposita, `noSubMatches` bleibt als Option.
**Empfehlung: als opt-in umsetzen und gegen die obigen Fälle messen.**

### 3. Kontextuelles Reranking (KenLM) — nicht im Core

Die Prämisse stimmt: `lookup("das")` liefert `dass` mit Distanz 1, und ohne Satz
lässt sich das nicht entscheiden. Aber die Lookup-API bekommt genau ein Token –
es gibt keinen Kontext, den ein Sprachmodell bewerten könnte. Das erfordert eine
Satz-API, ein Modell in dreistelliger MB- bis GB-Größe und JNI oder einen
ARPA-Parser. Der Hotpath bliebe unberührt, der Nutzen läge aber in einem anderen
Produkt (Satz-Rechtschreibprüfung), nicht in dieser Lookup-Bibliothek.

Günstigere Variante mit vorhandenen Daten: die bereits geladene Bigramm-Frequenz
für das Reranking von Komposita-Splits nutzen (`haus boot` vs. Alternativen) –
nicht für Homophone. **Nicht umgesetzt.**

## Viterbi-Komposita-Segmentierung (2026-11-05)

Opt-in über `SpellCheckSettings.builder().viterbiSegmentation(true)` (Default `false`), wirkt nur
im `lookupCompound`-Pfad.

### Problem
Die bisherige Heuristik probiert genau eine Schnittstelle (`lookupSplitWords`) und kombiniert die
besten Treffer der beiden Hälften. Dadurch können Zeichen verschwinden:
`krankenhausapotheke` → `kranken apotheke`, `sonnenblumenöl` → `sonnen blumen`,
`benutzeroberfläche` → `benutzer fläche`, `haustürschlüssel` → `haust schlüssel`.

### Umsetzung
- `SymSpellCheck.lookupViterbiSegmentation`: Dynamic Program über alle Positionen
  (`costs[]` + `previousBoundaries[]`), Segmentlängen 2..48, Wortlänge ≤ 48.
- Kosten pro Segment: `ln(maxFrequency / frequency)` plus `6.0` pro zusätzlicher Grenze.
  `maxFrequency` liefert `DataHolder.getMaxFrequency()` (neue Default-Methode, von
  `InMemoryDataHolder` beim Einfügen gepflegt).
- Das DP deckt per Konstruktion die komplette Eingabe ab, weil nur ganze Segmente verkettet werden.
- `lookupCompoundSegmentation` vergleicht das Ergebnis mit der alten Heuristik:
  - Kostet die Segmentierung nur die eingefügten Leerzeichen (`distance <= parts - 1`), gewinnt sie
    direkt — kein zweiter Lauf.
  - Andernfalls (das exakte DP kann einen falsch geschriebenen Teil nicht reparieren) läuft die
    alte Heuristik zusätzlich, und die geringere Distanz zum Eingabewort gewinnt.
- `noSubMatches` bleibt unverändert wirksam (betrifft nur die alte Heuristik).

### Ergebnisse (de-100k, `lookupCompound(word, 2, false)`)
| Eingabe | vorher | mit Viterbi |
|---|---|---|
| `krankenhausapotheke` | `kranken apotheke` | `kranken haus apotheke` |
| `sonnenblumenöl` | `sonnen blumen` | `sonnen blumen öl` |
| `benutzeroberfläche` | `benutzer fläche` | `benutzer ober fläche` |
| `haustürschlüssel` | `haust schlüssel` | `haus tür schlüssel` |
| `hausboot` | `haus boot` | `haus boot` |

Laufzeit 0,15–1,4 ms pro Aufruf (Dictionary-Lookups dominieren, nicht das DP).

### Nebenfund: Überlauf im bit-parallelen OSA
`bitParallelOsa` prüfte `distance > maxDistance + (textLength - 1 - j)`. Mit
`maxDistance = Integer.MAX_VALUE` (so ruft `lookupCompound` die finale Distanz auf) lief der
Ausdruck über und die Funktion gab sofort `-1` zurück — **jede** Compound-Distanz war dadurch
`-1`. Jetzt wird in `long` verglichen; Regressionstest in `Issue70RegressionTest.largeBudgetIsNotRejected`.

### Tests
`ViterbiSegmentationTest` (5 Tests): Setting-Roundtrip, vollständige Segmentierung, Erhalt
mittlerer Teile, Legacy-Verhalten bei deaktiviertem Flag, Fallback bei unbekanntem Wort.

## Deutsches 500k-Wörterbuch aus FrequencyWords (2026-11-05)

Quelle: [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords),
`content/2018/de/de_full.txt` (1.157.685 Zeilen, OpenSubtitles 2018).

### Bereinigung (`scripts/build-frequency-dictionary.py`)
- Kleinbuchstaben, nur `a-zäöüß` (optional Bindestrich über `--allow-hyphen`).
- Ziffern, Apostrophe, Satzzeichen und fremde Schriftsysteme fallen raus (275.143 Tokens).
- Groß-/Kleinschreibungs-Varianten werden summiert, dann absteigend sortiert und auf
  `--max-words` (Default 500.000) gekürzt.
- Ausgabe `term<TAB>frequency`; `--min-count`, `--min-length` und `--transform none|log10|sqrt`
  sind verfügbar.
- `--transform` bleibt bewusst auf `none`: die Rangfolge ist unter einer monotonen Transformation
  identisch, aber `SuggestionItem.count` wird additiv verwendet (`count + 2` bei Bigramm-Splits,
  `s1.count * s2.count / nMax`) und die Komposita-Kosten im Viterbi-Pfad sind Verhältnisse
  `ln(maxFreq / freq)`. Logarithmieren würde beide Pfade verändern, ohne das Ranking zu verbessern.

### Ergebnis
- 500.000 Einträge, 6,6 MB, Frequenzbereich 5.890.279 (`ich`) bis 1.
- Die gesuchten Komposita sind jetzt enthalten: `hausboot` (174), `krankenhaus` (16.645),
  `oberfläche` (1.719), `haustür` (853), `sonnenblumen` (65), `fußball` (1.779).
- Folge: `hausboot` und `krankenhausapotheke` sind exakte Treffer (Distanz 0), es kommt gar nicht
  erst zu einem Split. Damit greift `noSubMatches` seltener — es bleibt die Absicherung für Wörter,
  die auch im 500k-Bestand fehlen.

### Kosten (gemessen, `InMemoryDataHolder`, maxEditDistance 2, topK 25)
| prefixLength | Build | Heap | Lookup ALL (500k) |
|---|---|---|---|
| 7 | 3,9–4,0 s | ~304 MB | ~58 µs |
| 6 | 3,2–3,4 s | ~181 MB | ~137 µs |
| 5 | 3,7 s | ~150 MB | ~498 µs |

`prefixLength=7` bleibt gesetzt: halber Speicher spart 0,7 s Build, kostet aber 2,4× Lookup-Zeit.
Service-Start inklusive Spring Boot: 6,4 s. Demo-Antwort warm ~0,3 ms (`Verbosity.ALL`, topK 25).

## Whitespace-Prädikate (Perfect Hash) — geprüft, nicht übernommen

Der Perfect Hash aus Guavas `CharMatcher.Whitespace` (Multiplikation, Shift, ein
Tabellenzugriff, ein Vergleich) ist isoliert etwa 2× schneller als
`Character.isWhitespace` (0,75 ns gegenüber 1,5 ns pro Zeichen). Im Projekt bringt er
nichts: Der `DictionaryReader` prüft `byte` und ist mit der Vergleichskette bereits
optimal (2,0 ns/Byte), alle `char`-Prädikate werden nur wenige hundert Mal pro
Operation aufgerufen (< 0,1 % der Laufzeit). Details, exakte Tabellen, Multiplier,
Semantikvergleich und Messungen: [WHITESPACE.md](WHITESPACE.md).

## Weitere geprüfte Ideen (2026-11-07)

Kurzfazit der Internet-Recherche und der Messungen; Details in den jeweiligen
Analysedateien:

- **Compact Object Headers** (`-XX:+UseCompactObjectHeaders`, JDK 25): −5 % Heap
  (278 → 264 MB bei 500k Wörtern), keine Laufzeitverschlechterung.
  → Adoptieren als JVM-Flag. Siehe [COMPACT_OBJECT_HEADERS.md](COMPACT_OBJECT_HEADERS.md).
- **String Deduplication** (G1): kein Heap-Gewinn, +5 % Build-Zeit. Nicht adoptieren.
  Siehe [STRING_DEDUPLICATION.md](STRING_DEDUPLICATION.md).
- **AOT Cache** (JDK 25): 10–30 % schnellerer Startup, aber Wörterbuch (4 s)
  dominiert. Für Produktion sinnvoll. Siehe [AOT_CACHE.md](AOT_CACHE.md).
- **Memory-mapped Dictionary** (FFM): Lesen ist nur 1 % der Build-Zeit. Nicht adoptieren.
  Siehe [MEMORY_MAPPED_DICTIONARY.md](MEMORY_MAPPED_DICTIONARY.md).
- **Perfect Hash für Whitespace**: isoliert 2× schneller, in situ < 0,1 %.
  Siehe [WHITESPACE.md](WHITESPACE.md).

## Verbleibende Speed-Ideen (2026-11-07)

Umfassende Bestandsaufnahme der noch nicht ausprobierten Speed-Optimierungen,
JFR-Hotspots und realistischer Gewinn-Schätzungen:
[SPEED_OPTIMIZATION_OPPORTUNITIES.md](SPEED_OPTIMIZATION_OPPORTUNITIES.md).

Kleine Code-Verbesserung umgesetzt: `lookupCompound` baut den `joinedTerm` jetzt
per `StringBuilder` statt per `String.concat` (O(n) statt O(n²)). Kein messbarer
Gewinn bei der Lookup-Latenz, aber saubererer Code.
