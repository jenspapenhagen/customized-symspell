# AOT Cache (JDK 25, JEP 483) — Analyse für customized-symspell

Quelle: JEP 483 „Ahead-of-Time Class Loading & Linking“, `-XX:AOTCache`.

## 1. Was ist das?

Der AOT Cache speichert nach einem Trainingslauf die geladenen und gelinkten Klassen
in einer Datei. Bei späteren Starts wird diese Datei gemappt, statt die Klassen
erneut zu parsen und zu linken. Das beschleunigt den **Startup** (Klassenladen),
nicht die Dictionary-Verarbeitung.

## 2. Relevanz für dieses Repo

Service-Start (Spring Boot + 500k-Wörterbuch): ~6,4 s. Davon entfallen auf
Klassenladen/Spring-Initialisierung geschätzt 1,5–2,0 s, auf das Wörterbuch ~4 s.
Der AOT Cache kann nur den ersten Teil beschleunigen; die 4 s Dictionary-Build
bleiben unverändert.

## 3. Messung

Der AOT-Cache kann nur aus JAR-Dateien (nicht aus explodierten Klassenverzeichnissen)
erzeugt werden. Ein Trainingslauf über `LookupPerf` mit 82.765 Wörtern erzeugte
einen Cache von ~10,7 MB. Ein fairer Vorher/Nachher-Vergleich über den
Service-Start war mit den verfügbaren Test-Harnesses nicht möglich, weil
`LookupPerf` seine Testdaten als Dateisystem-Pfade (nicht als Ressourcen) lädt
und die Spring-Boot-Startzeit nicht erfasst.

Qualitative Erwartung laut JEP 483 und JDK-25-Dokumentation: **10–30 % schnellerer
Startup** bei typischen Serveranwendungen. Für dieses Repo bedeutet das grob
150–600 ms auf die ~6,4 s, also **2–9 %** des Gesamtstarts.

## 4. Bewertung

- Der Gewinn ist real, aber klein im Verhältnis zur Gesamtstartzeit, weil das
  Wörterbuch (4 s) dominiert.
- Der AOT Cache ist eine **reine Betriebsartefakt-Konfiguration**: keine
  Codeänderung, keine Checkstyle/SpotBugs-Auswirkung. Er wird beim Deployment
  erzeugt (`java -XX:AOTCacheOutput=app.aot -jar app.jar` beim ersten Start) und
  beim normalen Start genutzt (`java -XX:AOTCache=app.aot -jar app.jar`).
- Nachteil: Der Cache ist JDK-versions- und classpath-spezifisch; bei jedem
  Update muss er neu erzeugt werden. Für CI/CD ist das ein zusätzlicher Schritt.

## 5. Empfehlung

Für die Produktion adoptieren, sobald der Service in einem Container läuft:
Ein Eintrag im Startskript, der den Cache bei Bedarf neu erzeugt. Für die
Entwicklung und die CI (wo der Cache ohnehin neu wäre) nicht nötig.

Keine Codeänderung; Dokumentation in `README.md`/`GITHUB_ACTIONS_SETUP.md`
ergänzen, wenn der Container-Build eingerichtet wird.

## 6. Messkommandos (Trainingslauf)

```bash
# 1. Cache erzeugen (einmalig, z. B. im Container-Build)
java -XX:AOTCacheOutput=/opt/app.aot -jar symspell-service.jar
# 2. Normaler Start nutzt den Cache
java -XX:AOTCache=/opt/app.aot -jar symspell-service.jar
```
