# Memory-mapped Dictionary Loading (FFM) — Analyse für customized-symspell

Quelle: JDK 22+ Foreign Function & Memory API, `FileChannel.map(MapMode.READ_ONLY, ...)`.

## 1. Was ist das?

Statt die Dictionary-Datei in einen `byte[]` zu lesen (`Files.newInputStream` +
`System.arraycopy`), wird sie mit `FileChannel.map()` direkt in den Adressraum
gemappt. Das eliminiert Kopien und kann bei großen Dateien schneller sein, weil
das OS die Seiten bei Bedarf lädt.

## 2. Relevanz

`DictionaryReader.read(InputStream)` liest die Datei in einen 64-KB-Puffer und
parst byteweise. `read(Path)` öffnet einen `InputStream`. Die reine Lesezeit
für 500.000 Zeilen ist **40,3 ms** (gemessen, `ReaderBench`). Die Gesamt-Build-Zeit
für 500.000 Wörter beträgt ~4 s — das Laden ist also **~1 %** der Build-Zeit.
Der Rest ist `addItem` (Hash-Map-Inserts, Delete-Generierung, String-Allokation).

## 3. Messung

- `ReaderBench` auf `de-500k.txt`: **40,3 ms** für 500.000 Zeilen (nur Lesen,
  bester von 8 Läufen).
- Ein hypothetisches mmap würde diese 40 ms auf vielleicht 5–10 ms senken
  (OS-seitiges Lazy Loading, keine Kopie). Gewinn: ~30 ms.
- Build-Zeit insgesamt: ~4.000 ms. Gewinn: **< 1 %**.

## 4. Bewertung

- Der Gewinn ist real, aber winzig im Verhältnis zur Gesamtlaufzeit. Der Hot Path
  ist `addItem` (Hash-Map-Inserts + Delete-Generierung), nicht das Lesen.
- mmap bringt zusätzliche Komplexität: Lebenszyklus des `MemorySegment`, Cleanup
  (unmap), Plattformunterschiede bei Dateigrößen > 2 GB, und die Notwendigkeit,
  die Parser-Logik von `InputStream` auf `MemorySegment` umzustellen (der Parser
  arbeitet bereits auf `MemorySegment`, aber über `ofArray` auf einem Heap-Array).
- Die bestehende Implementierung nutzt bereits `MemorySegment`/`ValueLayout` für
  den SWAR-Scan — der Schritt zu mmap wäre klein, aber der Nutzen minimal.

## 5. Empfehlung

**Nicht adoptieren.** Der Gewinn (< 1 % der Build-Zeit) rechtfertigt die
Komplexität nicht. Falls in Zukunft das Wörterbuch auf mehrere Gigabyte wächst
oder das Parsen (nicht das Indexieren) zum Bottleneck wird, kann mmap nachgezogen
werden — die Parser-Logik ist bereits `MemorySegment`-basiert.

## 6. Messkommandos

```bash
# Lese-Benchmark (82k-Wörter-Dict)
java -cp "/tmp/opencode:$CP" ReaderBench \
    symspell-lib/src/test/resources/frequency_dictionary_en_82_765.txt
```
