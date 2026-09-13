# FMoon — Stufen 1 bis 3

Mondkalender im F-Stil. Java 8, NetBeans, Ant, Paket `com.dan.fmoon`.

Der Leitgedanke: **Die Phase wird nicht gezeichnet, sondern beleuchtet.** Der
Rechenkern liefert Phasenwinkel, Positionswinkel der hellen Gliedmitte und
Libration, und der Renderer setzt eine Kugel unter genau dieses Licht. Sichelform,
Sichelneigung und die perspektivische Verkürzung der Maria zum Rand hin ergeben
sich daraus von selbst.

## Was drin ist

**Rechenkern `com.dan.fmoon.astro`** — reines Java 8, ohne Fremdbibliothek, ohne Netz.

| Klasse | Inhalt |
|---|---|
| `FAstroTime` | Julianisches Datum, ΔT, Nutation, Schiefe, Sternzeit |
| `FSun` | Sonnenposition, volle VSOP87-Kurzfassung (Meeus Anhang III) |
| `FMoonPosition` | Mondposition, Tabellen 47.A und 47.B vollständig |
| `FMoonPhase` | Phasenwinkel, beleuchteter Anteil, Positionswinkel |
| `FMoonPhaseTimes` | Neu-, Voll- und Viertelmond nach Kapitel 49 |
| `FLibration` | Optische Libration, Positionswinkel der Achse |
| `FRiseSet` | Auf-/Untergang, Höhenmaximum, Meridiandurchgang |
| `FGeoLocation` | Orte; acht an Nord- und Ostsee vorgegeben |
| `FMoonEphemeris` | Fassade: alles für einen Zeitpunkt und Ort |

**Darstellung `com.dan.fmoon.render`** — `FMoonSurface` (gerechnete Oberfläche),
`FMoonShader` (Lommel-Seeliger statt Phong), `FMoonRenderer` (Kugel je Bildpunkt
geschnitten, zweifach überabgetastet, mit Zwischenspeicher).

**Gezeiten `com.dan.fmoon.tide`** — Vorhersage nach der Hafenzeit.

| Klasse | Inhalt |
|---|---|
| `FWaterBody` | Nordsee oder Ostsee; entscheidet, ob eine Vorhersage sinnvoll ist |
| `FTideStation` | Pegelort mit Hafenzeit und Tidenhub; acht Stationen vorgegeben |
| `FTideMoment` | ein Hoch- oder Niedrigwasser mit Zeit, Höhe und seinem Monddurchgang |
| `FHarbourTide` | die Rechnung: Durchgang + Hafenzeit = Hochwasser |
| `FHarbourTideModel` | speist die vorhandene `FTidalGauge` aus dieser Rechnung |

**Datenbank `com.dan.fmoon.db`** — Anbindung an Oracle 21c, Schema `DEMO`.

| Klasse | Inhalt |
|---|---|
| `FMoonDataSource` | Verbindung; braucht den Oracle-Treiber erst zur Laufzeit |
| `FMoonDao` | Lesen und Schreiben, ausschließlich über `FMN_API` |
| `FStationProvider` | Stationen aus der Datenbank, mit Rückfall auf die Konstanten |

**Komponenten `com.dan.fmoon`** — `FMoonGlobe`, `FMoonCalendar`, `FTideBoard`,
`FTideCurve`, `FStationMap`, `FOrbitView`, alle mit BeanInfo und gemalten
Palettensinnbildern, dazu `FMoonDemo` mit den Reitern Mond, Gezeiten, Karte und
Bahn.

## Bauen und starten

```
ant jar
java -jar dist/FMoon.jar
```

Klassenpfad ist `lib/FStyle.jar`. Aus dem Ordner heraus auch ohne Ant:

```
javac --release 8 -cp lib/FStyle.jar -d build/classes $(find src -name '*.java')
java -cp build/classes:lib/FStyle.jar com.dan.fmoon.FMoonDemo
```

## Prüfwerkzeuge

| Werkzeug | Was es prüft | Stand |
|---|---|---|
| `tools/FMoonAstroTest` | Rechenkern gegen die Beispiele bei Meeus | 67/67 |
| `tools/FMoonRenderTest` | Beleuchtete Fläche, Lage, Erdschein, schmale Sichel | 26/26 |
| `tools/FMoonTideTest` | Gezeiten gegen die Regeln, denen jede Tide gehorcht | 34/34 |
| `tools/FMoonDemoTest` | Bedienleiste vermessen, zwei Standbilder | 30/30 |
| `tools/FMoonBeanTest` | Palette: BeanInfo, Sinnbilder, Eigenschaften | 24/24 |
| `tools/FMoonMapTest` | Karte: Ebenen, Auswahl, Verhalten ohne Küstenlinie | 21/21 |
| `tools/FMoonOrbitTest` | Erklärbild: Winkel, Bahnlage, Erdnähe | 12/12 |
| `tools/FMoonSqlLint` | SQL-Skripte ohne Datenbank, Stammdaten gegen Java | 148/148 |
| `tools/FMoonDbSetup` | spielt `db/*.sql` ein, schreibt `build/db_setup.log` | — |
| `tools/FMoonDbCheck` | Durchstich gegen die laufende Datenbank | 61/61 |
| `tools/FMoonHarbourFit` | misst die Hafenzeit aus beobachteten Hochwasserzeiten | — |
| `tools/FMoonTideShot` | Standbilder: Nordsee gegen Ostsee, Monatskurve | — |

```
javac -cp build/classes:lib/FStyle.jar -d build/tools tools/*.java
java -cp build/classes:build/tools:lib/FStyle.jar FMoonAstroTest
```

Die Prüfwerte stammen aus *Astronomical Algorithms* und aus veröffentlichten
Konstanten — nicht aus eigenen Rechnungen. Dazu kommt eine Kreuzprobe: Der
Vollmondzeitpunkt aus der Phasenreihe (Kapitel 49) und die Nullstelle der
Längendifferenz aus den Positionsreihen (Kapitel 25 und 47) sind zwei völlig
getrennte Rechenwege; sie stimmen auf 0,17 Minuten überein.

## Entscheidungen mit Begründung

**Volle Reihen.** Sonne mit allen Gliedern L0–L5, B0–B1, R0–R4 (≈ 1″), Mond mit
beiden Tabellen vollständig (≈ 10″). Ein Fehler von 0,01° in der Sonnenlänge
würde einen Vollmondzeitpunkt schon um mehr als eine Minute verschieben.

**Lommel-Seeliger statt Phong.** Der Mond ist kein Lambert-Streuer. Lambert lässt
den Vollmond zum Rand hin dunkler werden, was er nachweislich nicht tut — er
erscheint als flache Scheibe von fast gleichmäßiger Helligkeit. Verwendet wird
0,86 Lommel-Seeliger plus 0,14 Lambert.

**Kugel je Bildpunkt geschnitten, nicht tesseliert.** Der Weg über `MeshFactory`
und `Rasterizer` wurde gebaut und wieder verworfen: Eine Kugel aus 128 × 96
Flächen hat am Rand Facetten von knapp 3° Breite, und genau dort liegt die Sichel
kurz vor und nach Neumond. Ihre Kante bekam eine sichtbare Treppe, und **unter
fünf Prozent Beleuchtung verschwand die Sichel vollständig** — der Mond war
schwarz, obwohl am Himmel eine deutliche Sichel stand. Bei orthografischer
Abbildung ist die Normale eine Wurzel: zu (x, y) gehört (x, y, √(1−x²−y²)), exakt
und schneller. `Mat4` und `Vec3` aus `com.dan.rayphong` bleiben in Gebrauch.
Eine feste Prüfung hält den Fall jetzt offen (Sichel bei 0,5 % muss hell sein).

**Oberfläche gerechnet, nicht als Bilddatei.** Siebzehn Maria an ihren
selenografischen Koordinaten, sieben Krater, drei Lagen Rauschen. Die JAR bleibt
eine Datei ohne Ressourcenordner. Ausgewertet wird über eine Wertetabelle von
1024 × 512 Stellen — die Vorschrift unmittelbar je Bildpunkt auszuwerten war
zehnmal langsamer, und bei dreißig Monden im Kalender war das deutlich zu spüren.

**Der Bezugszeitpunkt einer Kalenderzelle ist 21 Uhr Ortszeit.** Der Mond im
Kalender soll der sein, den man an diesem Abend sieht. Der Unterschied im
beleuchteten Anteil beträgt über einen Tag höchstens vier Prozent, aber er
entscheidet, auf welchen der beiden Nachbartage der Vollmond fällt.

## Gezeiten nach der Hafenzeit

Das Hochwasser folgt dem Mond: An einem gegebenen Ort tritt es immer im gleichen
Abstand zum **Meridiandurchgang** des Mondes ein. Dieser Abstand ist die
Hafenzeit (das Lunitidalintervall), eine Eigenschaft des Ortes. Weil der Mond
zweimal am Tag durch den Meridian geht — oben im Süden und unten unter dem
Horizont — gibt es zwei Hochwasser je Mondtag, alle 12 h 25 min.

Die Vorhersage ist damit eine Kette aus drei Gliedern, und `FTideBoard` zeigt
alle drei: Durchgang, plus Hafenzeit, ergibt Hochwasser. Wer das einmal gelesen
hat, kann es am Strand im Kopf nachrechnen.

Der Tidenhub folgt zwei Einflüssen: der **Spring-Nipp-Schwankung** (Sonne und
Mond ziehen zu Neu- und Vollmond gemeinsam) und der **Mondentfernung** (die
Gezeitenkraft fällt mit der dritten Potenz des Abstands). Beide sind
eingerechnet. Nicht gerechnet werden Wind, Luftdruck, Deklinationsungleichheit,
Flachwasserverformung und Oberwasser.

**Die Hafenzeiten der Nordsee sind am 13.09.2026 neu ausgerichtet worden.** Sie
standen rund zehn Stunden zu hoch. Aufgefallen ist das erst, als die harmonischen
Konstanten für Cuxhaven aus `com.dan.ftidalgauge` ein Jahr lang Hochwasser
erzeugten und daraus **1,45 h** gemessen wurden statt der angenommenen 11,90 h.
Alle fünf Nordseestationen sind um denselben Betrag verschoben: Die Abstände
entlang der Küste bleiben, der absolute Bezug ist gemessen.

Ob die *Abstände* stimmen, ist damit nicht gesagt — eine kurze Reihe beobachteter
Hochwasser für Büsum legt einen um gut eine Stunde anderen Abstand zu Cuxhaven
nahe. Die Ostsee blieb unangetastet: Dort ist keine Vorhersage sinnvoll, die
Hafenzeit wird nie benutzt, und sie um einen aus der Nordsee gemessenen Betrag zu
verschieben hieße, einer bedeutungslosen Zahl eine Bedeutung zu geben.

**Die Hafenzeiten und Tidenhübe bleiben gerundete Näherungswerte**, keine amtlichen
Angaben. Sie geben die Größenordnung und vor allem die Abfolge entlang der Küste
richtig wieder — die Tidewelle erreicht Helgoland zuerst, Cuxhaven rund eine
Stunde später —, ersetzen aber keine Gezeitentafel. `isApproximate()` ist bei
allen mitgelieferten Stationen wahr, und jede Anzeige sagt es.

**Die Ostsee bekommt keine Zeiten.** Der astronomische Tidenhub liegt dort bei
gut zehn Zentimetern und verschwindet unter Wind und Luftdruck. `FTideBoard`
zeigt für solche Stationen statt einer Vorhersage die Erklärung, warum es dort
keine gibt.

## Die Datenbank

Schema `DEMO` auf `PDBORCL`, Präfix `FMN_`. Das FCF-Schema von FCurvedField liegt
daneben und wird von keinem Skript angefasst.

```
db_setup.bat              alle Skripte einspielen
db_setup.bat 08_views.sql einzelne Skripte, jedes ist für sich wiederholbar
db_check.bat              Durchstich: füllen und beide Seiten prüfen
hafenzeit.bat             Hafenzeit aus Hochwasserzeiten messen
job.bat                   die nächtliche Pflege ein- und ausschalten
```

Alle schreiben ein Protokoll nach `build/` — das ist der Rückkanal, weil die
Datenbank von außen nicht erreichbar ist.

| Tabelle | Inhalt |
|---|---|
| `FMN_STATION` | Orte und Pegel, `SDO_GEOMETRY` Punkt, SRID 8307 |
| `FMN_EVENT` | Mondphasen und Apsiden, von Java gefüllt |
| `FMN_TIDE` | vorausberechnete Hoch- und Niedrigwasser |
| `FMN_GAUGE` | Messreihen, nach Jahr intervallpartitioniert |
| `FMN_NAMES` | Vollmondnamen |
| `FMN_RUN_LOG` | Protokoll der Läufe |

**Wo die Wahrheit liegt.** Java rechnet, die Datenbank speichert. Die vollen
Meeus-Reihen stehen nicht ein zweites Mal in PL/SQL — das wären 300 Glieder an
zwei Stellen. Was die Datenbank stattdessen hat, ist die **mittlere Phase**
(`FMN_API.mean_phase_jde`, eine Zeile Formel ohne jede Korrektur). Sie weicht vom
wahren Zeitpunkt um bis zu 0,6 Tage ab — das genügt vollauf, um einen falsch
eingetragenen oder vertauschten Eintrag zu erkennen, und schafft keine zweite
Wahrheit. Der Durchstich prüft das doppelt: einmal, dass kein echter Eintrag
auffällt, und einmal, dass ein absichtlich falscher sehr wohl auffällt.

**Zeiten stehen in UTC.** Ausnahmslos. Damit gibt es keine Sommerzeitlücke und
keine doppelte Stunde im Herbst; die Umrechnung in Ortszeit geschieht an einer
Stelle, in der Anzeige. `FMoonDao` gibt jedem Zeitstempel einen UTC-Kalender mit,
sonst verschöbe die Zeitzone der Java-Umgebung jeden Wert.

**Was Spatial hier wirklich beiträgt.** Nicht nur eine Karte: `FMN_WAVE_V` teilt
den geodätischen Abstand zweier Pegel durch die Differenz ihrer Hafenzeiten. Das
ergibt eine Ausbreitungsgeschwindigkeit, die in der Größenordnung der
Flachwasserwelle liegen muss (√(g·h), bei 10–30 m Tiefe also 10–17 m/s). Eine
echte Gegenprobe der Näherungswerte, die ohne Geometrie nicht möglich wäre.

**Die Übungsmesswerte sind erfunden.** `FMN_GAUGE_RAW` trägt in jeder Zeile
`UEBUNG-ERFUNDEN`. Es sind keine Messwerte, sie stammen von keiner Behörde und
von keinem Pegel — sie werden gerechnet, damit der Weg „fremde Tabelle →
`FMN_GAUGE` → Vergleich mit der Vorhersage" durchgespielt werden kann.

## Fallstricke, die hier schon zugeschlagen haben

- **`getLocation()` gehört `Component`.** Die Eigenschaft heißt deshalb
  `observerLocation`. Ein Überschreiben ist unmöglich, die Rückgabetypen
  widersprechen sich (`Point` gegen `FGeoLocation`).
- **Höhenmaximum ≠ Meridiandurchgang.** Beim Mond laufen beide bis zu fünf
  Minuten auseinander, weil sich die Deklination um bis zu 0,2° je Stunde ändert.
  `FRiseSet.transitJdUt` ist das Höhenmaximum, `FRiseSet.meridianTransit` der
  Stundenwinkel null. **Für die Hafenzeit in Stufe 2 ist der Meridiandurchgang
  die richtige Größe.**
- **Eine zu breite FlowLayout-Reihe im BoxLayout** bricht um und bekommt die Höhe
  einer Zeile; die zweite wird lautlos abgeschnitten. Die Reihe „Ansicht" lag bei
  98 % und musste geteilt werden. `FMoonDemoTest` vermisst jetzt jede Reihe.
- **`javac` und `java` können verschiedene Versionen sein.** Auf Daniels Rechner
  übersetzt ein JDK 22, es läuft aber eine Java-21-Laufzeit. Ohne
  `-source 8 -target 8` entstehen Klassendateien der Version 66, die diese
  Laufzeit nicht laden kann (`UnsupportedClassVersionError`). Das gilt auch für
  die Prüfwerkzeuge, nicht nur für die Quellen. `pruefen.bat` schreibt beide
  Versionsnummern jetzt oben ins Protokoll.
- **Beim Zurückschreiben die Größe am Ziel nachsehen.** Eine zweite Übertragung
  unter demselben Ablagenamen meldete Erfolg und schrieb trotzdem die alte
  Fassung. Der Erfolgsmeldung allein ist nicht zu trauen.
- **`FTidalGauge` zeichnet ihre Kurve aus den Partialtiden, nicht aus
  `getLevelAt`.** Mit dem Hafenzeit-Modell stand im Kopf „0 Partialtiden" und die
  Kurve blieb leer. Ihre **Pegelszene** dagegen liest den Wasserstand aus dem
  Modell und stimmt. Deshalb wird von der Komponente nur die Szene benutzt; die
  Kurve zeichnet `FTideCurve` aus derselben Rechnung, aus der auch die Zeiten
  stammen. Die Hafenzeit in äquivalente Partialtiden zu übersetzen wäre möglich
  gewesen, hätte aber zwei Wahrheiten geschaffen, die um Minuten auseinanderlaufen.
- **Der nächste Monddurchgang gehört nicht zum nächsten Hochwasser.** Weil die
  Hafenzeit elf bis zwölf Stunden beträgt, stammt das nächste Hochwasser fast
  immer aus einem Durchgang, der schon vorüber ist. Die Rechenkette wird deshalb
  vom Ergebnis her aufgerollt: erst das nächste Hochwasser suchen, dann seinen
  mitgeführten Durchgang anzeigen.
- **`getLevelAt` wird beim Zeichnen für jede Bildspalte aufgerufen.** Die rohe
  Rechnung würde dabei jedes Mal Monddurchgänge suchen. `FHarbourTideModel` hält
  deshalb ein Fenster von Ereignissen im Zwischenspeicher und interpoliert nur
  noch — mit einer Prüfung, dass beide Wege auf 10⁻⁹ m dasselbe liefern.
- **Die Datenbank ist von der Cloud-Seite nicht erreichbar.** Deshalb läuft alles
  über `.bat` plus Protokoll, und deshalb gibt es `FMoonSqlLint`: Was sich ohne
  Datenbank prüfen lässt, wird vorher geprüft — Zerlegung, Klammern, Hochkommata,
  nur ASCII, und vor allem, ob die Stammdaten im Skript mit den Java-Konstanten
  übereinstimmen. Zwei Stellen mit denselben Zahlen sind sonst zwei Wahrheiten.
- **`CREATE OR REPLACE PACKAGE` verwirft den Body.** `06_api.sql` und
  `07_api_body.sql` gehören immer zusammen eingespielt; beide Dateien sagen es.
- **`DROP TYPE ... FORCE` macht jedes Package ungültig**, das den Typ benutzt.
  `02_types.sql` und die beiden API-Skripte ebenfalls immer zusammen.
- **Ein Java-Escape in einem SQL-String** bleibt stehen — `'Erdn\u00e4he'` wäre so
  in der Datenbank gelandet. `FMoonSqlLint` sucht danach.
- **Meeus druckt Zwischenwerte und Endwerte.** Zwei meiner Sollwerte waren aus
  dem Gedächtnis der jeweils falsche: die mittlere Phase statt des korrigierten
  Neumonds, und der Knoten aus einem Beispiel, das ihn gar nicht nennt. Prüfwerte
  gehören nachgeschlagen, nicht erinnert.
- **Ein Messfenster kürzer als die Periode misst das Fenster.** Der anomalistische
  Monat schwankt zwischen 24,6 und 28,6 Tagen, und diese Schwankung hat selbst
  eine Periode von rund 206 Tagen. Über zwei Jahre gemittelt bleibt ein Zehntel
  Tag Fehler; geprüft wird über zwanzig Jahre.

### Was der erste echte Lauf gefunden hat

Sieben von siebenundvierzig Prüfungen waren rot. Keine davon war ein Zufall.

- **`INTERVAL '365' DAY` passt nicht.** Ein Tagesintervall hat ohne Angabe zwei
  führende Stellen; drei Ziffern gehen nicht hinein, und Oracle antwortet mit
  ORA-01873 — aber erst beim Übersetzen des Blocks, nicht beim Einspielen. Die
  Prozedur `FMN_NIGHTLY` war deshalb ungültig, und zwei weitere Prüfungen fielen
  hinterher. Jetzt steht dort `INTERVAL '1' YEAR`, und `FMoonSqlLint` sucht nach
  Intervallliteralen mit mehr als zwei Ziffern.
- **`LAG` ohne die richtige Partitionierung.** `FMN_TIDE_V` rechnete den Abstand
  zum vorigen Ereignis *derselben Station* — aber die Folge lautet HW, NW, HW, NW.
  Herauskam die halbe Tide: 6,17 h statt 12,42 h. Der Fehler war gefährlich, weil
  6 h ebenfalls plausibel aussieht. `IS_HIGH_WATER` gehört in die Partition;
  daneben steht jetzt `ABSTAND_FOLGE_H` für den gemischten Abstand.
- **Die Prüfung verzieh genau die Abweichung, die sie finden sollte.** In der
  Datenbank stand `'Buesum'`, im Java-Code `"Büsum"` — und `FMoonSqlLint` ließ die
  umschriebene Form durch. Also grün hier, und im Durchstich zwei Stationen, die
  sich nicht wiederfanden. Die Skripte bleiben ASCII (Zeichensatz des Treibers),
  der Name kommt jetzt über `UNISTR('B\00FCsum')` in die Spalte, und verglichen
  wird Zeichen für Zeichen.
- **Toleranz gegen die Wirklichkeit gesetzt.** Die mittlere Phase ist eine Gerade,
  die wirkliche Phase schwingt um sie herum: Voll- und Neumond bleiben innerhalb
  von 14,2 h, die Viertel gehen bis 19,2 h — sie tragen bei Meeus einen Term, den
  Voll- und Neumond nicht haben. Mit 15 h Toleranz prüfte der Durchstich nicht
  gegen einen Fehler, sondern gegen die Astronomie. Jetzt 24 h.
- **Wieder das zu kurze Fenster.** Der synodische Monat aus zwölf Abständen ergab
  29,4735 statt 29,5306 Tage — ein einzelner Neumond weicht um einen Viertel Tag
  von der Mittelbewegung ab, und durch zwölf geteilt bleibt davon mehr stehen als
  die Toleranz. Über zehn Jahre (123 Abstände) kommen 29,5266 heraus. Dieselbe
  Lehre wie beim anomalistischen Monat in Stufe 1, an neuer Stelle.
- **Eine Materialized View `ON DEMAND` ist nach dem Einspielen leer** — und das ist
  richtig, nicht kaputt: `FMN_EVENT` war noch leer, als sie gebaut wurde. Die
  Prüfung geht jetzt auf den Unterschied vorher/nachher und zählt gegen die
  Tabelle nach.

### Und was der zweite Lauf noch fand

Von siebenundvierzig waren noch zwei rot — und eine grüne war die lehrreichste.

- **Dieselbe Zahl an vier Stellen.** Die Toleranz der Phasenprüfung stand als `15`
  in der Package-Spezifikation, im Rumpf, in `FMN_NIGHTLY` und im Schaukasten.
  Geändert wurden beim ersten Durchgang zwei davon — also lief der Durchstich grün
  und der nächtliche Job scheiterte mit `ORA-20020: 47 Phasen auffällig`. Der Wert
  steht jetzt einmal, als `FMN_API.C_PHASE_TOL_H`, mit seiner Begründung daneben;
  alle Aufrufer rufen ohne Argument. `FMoonSqlLint` schlägt an, wenn ein Skript ihn
  noch einmal hinschreibt — und hat dabei sofort die vierte Stelle gefunden, die
  ich selbst übersehen hatte.
- **Eine Prüfung auf den Zustand statt auf das Verhalten.** „Auffrischen füllt die
  Jahresübersicht" verglich vorher mit nachher — aber wann eine `ON DEMAND`-View
  zuletzt aufgefrischt wurde, hängt vom Ablauf ab und ist kein Merkmal der
  Datenbank. Jetzt fügt der Durchstich selbst eine Zeile ein, weist nach, dass die
  Übersicht sie **noch nicht** kennt, frischt auf, weist nach, dass sie jetzt
  stimmt, und räumt wieder weg. Beim ersten Anlauf war auch das noch falsch: Die
  Prüfung *nahm an*, die Übersicht sei um genau eine Zeile im Rückstand — sie stand
  aber auf null, weil sie seit dem Einspielen nie aufgefrischt worden war. Derselbe
  Fehler eine Stufe feiner. Der Ausgangszustand wird jetzt hergestellt (einmal
  auffrischen), nicht angenommen.
- **Eine grüne Prüfung, die nichts mehr prüfte.** „Vollmonde bekommen ihren Namen:
  124 von 13" — die Bedingung lautete `mindestens zwölf` und blieb grün, als der
  Durchstich auf zehn Jahre umgestellt wurde. Sie fordert jetzt *jeden*. Eine
  Prüfung, deren Ausgabe man nicht mehr liest, ist keine.

## Die Karte

`FStationMap` zeigt die acht Stationen auf der Küste, mit den Laufzeiten der
Tidewelle dazwischen. Drei Entscheidungen stecken darin.

**Der Kartenbetrachter wurde nicht gebaut.** `com.dan.fgeochart.FGeoChart` liegt
seit jeher in `FStyle.jar` — Ebenen, Projektion, Gitter, Zoom, Schwebeanzeige,
ein Styler in Seekartenfarben. Wie `FTidalGauge` in Stufe 2, und wie damals
stimmt der Fund nicht ganz: `FGeoChart` meldet **keinen Klick**. Er gibt aber
`toLon(x)` und `toLat(y)` heraus. Aus dem Mausklick wird damit ein Punkt auf der
Erde, und welche Station dort am nächsten liegt, beantwortet
`FMN_API.NEAREST_STATION` mit `SDO_NN`. Bei acht Stationen wäre die Rechnung in
Java gleich schnell — aber sie bleibt richtig, wenn es achthundert werden.

**Die Küstenlinie ist geliehen, nicht kopiert.** Sie kommt aus
`OGP.GEMEINDEN_SH`, den OpenStreetMap-Daten, die ohnehin auf derselben Datenbank
liegen: Der Außenrand dieser Gemeindeflächen *ist* die Küste. FMoon legt eine
Sicht darüber und holt sich die Umrisse als reine Zahlenreihe
(`FMN_API.COAST`) — vereinfacht mit `SDO_UTIL.SIMPLIFY`, gefiltert über den
räumlichen Index mit `SDO_FILTER`, damit nicht 1 892 Flächen über die Leitung
gehen. Eine eigene Küstentabelle wäre die zweite Wahrheit, und sie wäre veraltet,
sobald jemand die OSM-Daten neu einspielt.

### Die nächtliche Pflege

`FMN_NIGHTLY_JOB` frischt die Jahresübersicht auf, prüft die eingetragenen
Phasen und Hochwasser gegen die Datenbankseite und räumt alte Protokollzeilen
weg. Mondereignisse rechnet er **nicht** — die vollen Reihen stehen in Java und
sollen nicht ein zweites Mal in PL/SQL gepflegt werden.

```
job.bat          zeigt den Stand, ändert nichts
job.bat an       einschalten, mit einem Lauf sofort zur Probe
job.bat aus      ausschalten (der Job bleibt angelegt)
job.bat jetzt    ein Lauf von Hand, unabhängig vom Schalter
```

Zwei Dinge, die man dabei wissen muss:

- **Der Job lässt sich scheitern.** Findet die Prüfung etwas außerhalb der
  Toleranz, wirft `FMN_NIGHTLY` einen Fehler, und der Lauf steht als `FAILED`
  in `USER_SCHEDULER_JOB_RUN_DETAILS`. Ein Job, der immer grün ist, wird nicht
  angesehen. Nach vier Fehlschlägen in Folge schaltet Oracle ihn von selbst ab —
  `job.bat` zeigt das.
- **Ein Neuaufbau schaltet ihn wieder aus.** `db_setup.bat` spielt
  `09_scheduler.sql` ein, und das legt den Job neu an, mit `enabled => FALSE`.
  Das ist gewollt: Ein Aufbaulauf soll nichts anschalten, was danach jede Nacht
  von selbst arbeitet. Nach jedem `db_setup.bat` also wieder `job.bat an`.

Die vier Skripte in `db/` tragen deshalb **keine Nummer** — Nummern sagen
„gehört in die Aufbaureihenfolge", und das tun sie nicht. `FMoonSqlLint` prüft,
dass keines von ihnen in `FMoonDbSetup.STANDARD` steht und dass der Aufbau den
Job ausgeschaltet anlegt.

Dafür braucht `DEMO` ein Leserecht, das **du als `OGP` gibst** — es ist
absichtlich nicht Teil von `db_setup.bat`:

```
sqlplus ogp/ogp@//localhost:1521/PDBORCL @db/11_grant_ogp.sql
```

Fehlt es, ist das kein Fehler, sondern ein Zustand: Die Karte zeigt Stationen,
Strecken und Gradnetz und schreibt darunter, warum die Küste fehlt. Eine
gezeichnete Ersatzküste wäre die schlechtere Wahl — sie sähe aus wie eine Aussage
über die Wirklichkeit, ohne eine zu sein. Eine Prüfung hält das fest.

**Ein Fächer, keine Kette.** Der erste Entwurf verband die Nordseestationen in
der Reihenfolge ihrer Hafenzeit zu einem Linienzug. Die Zahlen daran stimmten,
aber das Bild log: Ein Linienzug sieht aus wie ein Weg, und Helgoland → List →
Büsum → Cuxhaven → Husum ist nicht der Weg der Welle, sondern nur die Folge der
Ankunftszeiten — er zickzackt quer durch die Bucht. Jetzt geht von der zuerst
erreichten Station ein Strahl zu jeder anderen, beschriftet mit dem Verzug
(Husum: +69 min). Das ist genau die Aussage, die in den Daten steckt.

## Das Erklärbild

`FOrbitView` beantwortet die Frage, mit der jeder Mondkalender anfängt: *Warum
ist heute Vollmond?* Links die Bahn von oben, rechts derselbe Augenblick von der
Erde aus. Steht der Mond der Sonne gegenüber, sieht man rechts die volle Scheibe;
steht er daneben, eine Sichel. Der Zusammenhang wird nicht behauptet, sondern
gezeigt — **beide Bilder kommen aus derselben Rechnung.**

Genau das ist auch die Prüfung: Zu jeder der fünfzig Phasen eines Jahres muss der
gezeichnete Winkel 0°, 90°, 180° oder 270° sein. Gemessen wurde eine größte
Abweichung von **0,0026°**. Stimmte das nicht, wäre das linke Bild eine hübsche
Behauptung neben dem rechten.

Bei einem Schaubild ist die Versuchung groß, es hübsch statt richtig zu machen.
Deshalb steht fest, was echt ist:

- **Der Winkel Sonne–Erde–Mond** — aus den gerechneten ekliptikalen Längen,
  angeschrieben in Grad.
- **Die Bahn.** Echte Exzentrizität 0,0549, Erde in einem Brennpunkt — und die
  Ellipse ist in die **echte Richtung der Erdnähe** gedreht (Länge des Perigäums,
  also mittlere Länge minus mittlere Anomalie). Der erste Entwurf legte die große
  Achse einfach waagerecht; dann stand der Mond bei 404 000 km weit außerhalb
  seiner eigenen Bahnlinie. Ein Bild, das eine Bahn zeichnet und den Körper
  daneben setzt, erklärt nichts.
- **Größen und Sonnenabstand sind es nicht.** Maßstabsgerecht wäre die Erde zwei
  Bildpunkte groß und die Sonne 150 Bildschirme weit weg. Das Bild sagt es selbst,
  unten links, und der Hinweis ist voreingestellt an — ihn abzuschalten macht das
  Bild hübscher und unehrlicher.
- **Die Bahnneigung fehlt**, von oben ist sie unsichtbar. Sie ist aber der Grund,
  warum nicht jeder Vollmond eine Finsternis ist, und steht als Satz daneben.

Die schönste Zahl fällt dabei nebenbei ab. Der gezeichnete Mond folgt seinem
*gerechneten* Abstand, die Bahnlinie einer reinen Keplerellipse — und beide gehen
im Mittel um **2 746 km** auseinander, schlimmstenfalls um 6 974 km. Das ist kein
Fehler, das ist die Störung durch die Sonne. Die Prüfung verlangt deshalb
ausdrücklich, dass die Abweichung **nicht null** ist: Wäre sie es, hätten wir den
Abstand aus der Ellipse genommen statt aus der Rechnung, und das Bild zeigte eine
Bahn, die es nicht gibt.

## Echte Hafenzeiten eintragen

Die mitgelieferten Hafenzeiten sind Näherungen, und der Weg, sie durch echte
Werte zu ersetzen, ist jetzt fertig gebaut — ohne dass dafür eine einzige Zahl
erfunden wurde.

**Wenn du eine Gezeitentafel hast**, geht es über `FMN_API.SET_HARBOUR_TIME`.
Ein `UPDATE` täte es auch, aber dann fehlt alles, was die Prozedur mitbringt:
Sie weist unglaubwürdige Werte ab (eine Hafenzeit über 12,42 h ist keine
Hafenzeit, sondern ein Vorzeichenfehler), sie nennt einen unbekannten
Stationscode beim Namen statt stillschweigend null Zeilen zu ändern, sie
verlangt eine Quelle, sobald das Näherungskennzeichen fallen soll, und sie
schreibt den Wechsel ins Protokoll.

```sql
BEGIN
  fmn_api.set_harbour_time('BUESUM', 11.42, NULL,
    'BSH Gezeitenkalender 2026', DATE '2026-09-13', 'N', 7.5);
END;
```

**Wenn du eine Reihe von Hochwasserzeiten hast**, musst du die Hafenzeit nicht
selbst ausrechnen: `hafenzeit.bat` tut es. Das Werkzeug rechnet zu jedem
Hochwasser den Meridiandurchgang mit demselben Kern, der auch die Vorhersage
macht, und mittelt die Abstände — ein Muster für das Eingabeformat liegt in
`db/hafenzeiten_muster.txt`.

```
hafenzeit.bat meine_zeiten.txt              nur rechnen und anzeigen
hafenzeit.bat meine_zeiten.txt -schreiben   gleich eintragen
```

Zwei Dinge daran sind Absicht:

- **Es weist Reihen unter 29,53 Tagen zurück.** Die Hafenzeit ist keine
  Konstante; sie schwankt über den Spring-Nipp-Zyklus um eine halbe Stunde und
  mehr. Wer über zehn Tage mittelt, misst den Ausschnitt des Zyklus, der
  zufällig in den zehn Tagen lag — genau der Fehler, der in diesem Projekt schon
  dreimal aufgetreten ist. `-kurz` hebt die Sperre auf, schreibt den Vorbehalt
  dann aber in die Quellenangabe, und die wandert mit in die Datenbank.
- **Gemittelt wird auf dem Kreis.** Die Hafenzeit ist eine Phase mit der Periode
  eines halben Mondtages. Ein arithmetisches Mittel aus 0,1 h und 12,3 h ergäbe
  6,2 h — einen Wert, der mit keiner der beiden Beobachtungen etwas zu tun hat.

**Und wenn gar keine Zeiten vorliegen**, gibt es noch eine Quelle, die schon im
Haus ist: die harmonischen Konstanten in `com.dan.ftidalgauge` (Cuxhaven,
Wilhelmshaven). `hafenzeit.bat -harmonik` lässt sie ein volles Jahr Hochwasser
erzeugen und misst daraus die Hafenzeit. Das ist eine physikalische Reihe statt
einer Schätzung — aber **keine amtliche Gezeitentafel**, sondern nur so gut wie
die Konstanten in der Bibliothek, deren Herkunft dort nicht dabeisteht. Das
Näherungskennzeichen bleibt deshalb auf `'J'`.

**Die Herkunft gehört zum Wert.** `FMN_STATION` führt jetzt `SOURCE`,
`SOURCE_DATE` und `HW_INTERVAL_SD_MIN` (die gemessene Streuung in Minuten — die
ehrlichste Angabe von allen, denn sie sagt, wie gut eine einzige Zahl diesen Ort
überhaupt beschreiben kann). Die Datenbank besteht darauf: `IS_APPROX = 'N'`
ohne Quelle weist ein Constraint zurück. Und die Anzeige lässt den Hinweis nicht
einfach weg, sondern tauscht ihn — aus „gerundete Näherungswerte" wird
„Hafenzeit nach BSH Gezeitenkalender 2026".

### Was der Kartenlauf gefunden hat

**Ein Fehler, neun Folgefehler.** `T_FMN_COAST_OBJ` und `T_FMN_COAST_TAB` standen
im Kartenskript `12_coast.sql` — dort, wo sie gebraucht werden. `FMN_API` nennt
sie aber schon in seiner Spezifikation, und die wird in `06` eingespielt, sechs
Skripte früher. Ergebnis: `PLS-00201` in der Spec, `PLS-00905` im Rumpf, und
danach fiel alles hinterher, was `FMN_API` benutzt — der Scheduler, der Import,
die Prüfungen.

Die Regel dahinter ist einfach und gilt ohne Ausnahme: **Typen gehören in `02`.**
Auch die, die nur ein einziges spätes Skript braucht.

Tückisch war der Lauf trotzdem: Weil `12_coast.sql` selbst `fmn_api.log_run`
aufruft, hat Oracle `FMN_API` dabei neu übersetzt — und da existierten die Typen
inzwischen. Am Ende stand das Schema also fast richtig da, und ein zweiter Lauf
wäre grün gewesen, ohne dass irgendetwas repariert worden wäre. Eine
Reihenfolge, die sich beim zweiten Mal selbst heilt, ist keine Reihenfolge.

`FMoonSqlLint` prüft das jetzt: Jeder Typ, den die Spezifikation nennt, muss in
einem Skript mit kleinerer Nummer angelegt sein. Die Prüfung wurde gegen den
echten Fehler gegengeprüft — nimmt man die Typen aus `02` wieder heraus, wird sie
rot.

### Und was der zweite Kartenlauf gefunden hat

**`SDO_UTIL.SIMPLIFY` rechnet bei geodätischen Daten in Metern, nicht in Grad.**
Ich hatte `0.002` übergeben und „rund 200 m" dazugeschrieben — in Wahrheit zwei
Millimeter, also gar keine Vereinfachung. Heraus kamen 1 352 Ringe mit **433 742
Punkten**, wo 200 m Toleranz einen Bruchteil davon liefern. Bemerkenswert daran:
Es war kein Fehler, der etwas kaputt macht. Die Karte hätte funktioniert, nur
zäh — und niemand hätte je nachgesehen, warum.

**Eine Prüfung, die nur „irgendwas stimmt nicht" sagt, ist zu wenig.** Meine
erste Fassung verlangte, dass jeder Umrisspunkt im abgefragten Fenster liegt, und
meldete „mindestens einer nicht". Das war doppelt schlecht: Die Behauptung war
falsch — `SDO_FILTER` liefert *ganze* Flächen, die das Fenster berühren, und die
dürfen darüber hinausragen — und die Meldung nannte den Übeltäter nicht. Jetzt
schreibt der Durchstich den tatsächlichen Ausschnitt der Umrisse ins Protokoll
und prüft zwei Dinge, die wirklich falsch sein können: ob das Ergebnis überhaupt
noch Schleswig-Holstein sein *kann*, und ob Länge und Breite vertauscht sind —
hier ist die Breite immer größer als die Länge, und ein vertauschtes Paar fällt
auf der Karte sonst nur als leerer Ausschnitt auf.

### Ein kaputter Umriss in den OSM-Daten

Nachdem die Vereinfachung stimmte (26 306 statt 433 742 Punkte), sagte die
Prüfung endlich etwas Brauchbares: Die Breite lag mit 53,35 bis 55,06 exakt auf
Schleswig-Holstein — die Länge aber reichte von 7,00 bis **14,27**. Das letzte
liegt in Brandenburg.

Douglas-Peucker *entfernt* Stützpunkte, es erfindet keine. Der Punkt steht also
so in den Daten: eine Gemeindefläche in `OGP.GEMEINDEN_SH` mit einem verirrten
Stützpunkt, wie er bei OSM-Relationen vorkommt. Gezeichnet zieht so ein Ring
einen Balken quer über die Karte.

Behandelt wird das **in der Anzeige, nicht in den Daten**: Die OSM-Daten gehören
`OGP` und bleiben, wie sie sind. `FMoonDao` überspringt Ringe, die über drei Grad
ausgedehnt sind — Schleswig-Holstein als Ganzes ist gut drei Grad breit, eine
einzelne Gemeinde keine halben —, **zählt sie und sagt es**: im Durchstich mit
Flächennummern, unter der Karte als Satz. Ein stilles Weglassen wäre eine
Anzeige, die etwas verschweigt.

### Der Durchstich läuft

Vier Anläufe hat es gebraucht; seitdem sind **47 von 47** grün. Zehn Befunde
insgesamt, und bemerkenswert daran ist, was *nicht* darunter war: kein einziger
Rechenfehler. Jeder einzelne war eine **Annahme, die nie geprüft wurde** — über
ein Intervallformat, über die Reihenfolge in einem Fenster, über eine
Schreibweise, über eine Toleranz, über die Länge eines Messfensters, über den
Zustand einer Materialized View, über den Umfang eines Laufs. Die Skripte selbst
— Reihenfolge, Abhängigkeiten, Spatial, Partitionierung, Objekttypen, pipelined
functions — liefen auf Anhieb.

## Offen

- **Die echten Werte selbst.** Der Weg dahin steht (siehe oben), die Zahlen
  fehlen: Eine amtliche Quelle war von hier aus nicht erreichbar. Sobald der
  BSH-Gezeitenkalender oder eine eigene Messreihe vorliegt, ist es ein Aufruf.
- Nach dem Eintragen neuer Werte: `db_setup.bat` ist dafür **nicht** nötig —
  `set_harbour_time` ändert die Stammdaten im Betrieb.
- Monatswechsel im Kalender dauert rund eine Viertelsekunde Rechnung plus
  Zeichnung. Bei Bedarf in einen Hintergrundfaden verlegen.
