---
title: Wie genau ist KI-gestütztes Food-Logging?
date: 2026-07-28
description: Getippte Einträge mit Mengenangabe sind nahezu gelöst; Tellerfotos sind es nicht. Kurze Mahlzeitnotizen und Videoclips schließen die Lücke nicht. Gemessen an gelabelten Datensätzen.
draft: true
---

## 1 Einleitung

Chompass folgt dem BYOK-Prinzip: Nutzer bringen einen eigenen Cloud-KI-Schlüssel mit (oder betreiben Gemma 4 lokal auf dem Android-Gerät). Die Genauigkeit der Lebensmittelanalyse hängt daher überwiegend vom gewählten Modell ab, nicht von einer Chompass-spezifischen Eigenschaft. Wir messen die Genauigkeit gegen gelabelte Datensätze und veröffentlichen die Zahlen, anstatt eine einzelne Genauigkeitsangabe zu behaupten.

## 2 Methodik

Alle nachfolgenden Werte stammen aus einem Offline-Testrahmen (Research Harness) gegen gelabelte Datensätze mit bekannten Ground-Truth-Kalorien- und Makronährstoffwerten. Die vollständige Methodik, alle Testläufe und die Rohdatentabellen sind im [Benchmark-Status-Dokument](https://codeberg.org/fitguy/chompass/src/branch/main/docs/FOOD_ACCURACY_BENCHMARK_STATUS.md) auf Codeberg hinterlegt. Eine kürzere Zusammenfassung findet sich in [ACCURACY.md](https://codeberg.org/fitguy/chompass/src/branch/main/docs/ACCURACY.md).

## 3 Kernergebnisse

| Eingabemethode | Metrik | Ergebnis | Datensatz |
|---|---|---|---|
| **Text mit angegebener Menge** | WMAPE (kcal+Protein+Kohlenhydrate+Fett) | **5,7 %** | 42 USDA-FNDDS-Einträge |
| **Text mit angegebener Menge** | Innerhalb ±20 % der wahren Kalorien | **90 %** | 42 USDA-FNDDS-Einträge |
| **Nur Foto (bestes getestetes Bezahlmodell)** | WMAPE | **32,3 %** | 50 reale Mahlzeitfotos ([January Food Benchmark](https://github.com/January-ai/food-scan-benchmarks)) |
| **Nur Foto (bestes getestetes Bezahlmodell)** | Innerhalb ±20 % der wahren Kalorien | **50 %** | 50 reale Mahlzeitfotos |
| **Nur Foto (kostenloses, on-device-taugliches Modell)** | WMAPE | 39,8 % | 50 reale Mahlzeitfotos |

WMAPE = gewichteter mittlerer absoluter prozentualer Fehler über Kalorien, Protein, Kohlenhydrate und Fett. Niedriger ist besser. Diese Werte beruhen auf Food-Analyse-Prompts, die dem in der App ausgelieferten Prompt entsprechen, ausgeführt gegen dieselben Manifeste für jedes getestete Modell.

Die Zeile „Text mit angegebener Menge“ ist **nicht** gleichzusetzen mit dem Eintippen eines Mahlzeitnamens neben einem Foto. Der FNDDS-Datensatz enthält Einträge wie `Chicken breast, roasted, 150 g` oder `1 cup oatmeal (240 g)` — Identität **plus** Gramm- oder Haushaltsmaßangabe. Deshalb liegt der Wert bei rund 6 % WMAPE. Die Foto-Zeilen betreffen reale, angerichtete Mahlzeiten **ohne** Mengenangabe. Der Vergleich dieser beiden Spalten ist ein Vergleich zwischen mengenbasierter Nachschlage-Eingabe und freier Tellerschätzung.

<figure>
  <img src="/img/blog/accuracy/text-vs-photo.png" alt="Two bar charts comparing portioned typed entry, best paid photo, and free photo: WMAPE 5.7% vs 32.3% vs 39.8%, and within ±20% calories 90% vs 50% vs 32%." width="800" height="450" loading="lazy">
  <figcaption>Getippte Eingabe mit Mengenangabe (FNDDS 42) vs. Tellerfotos (JFB 50). WMAPE: niedriger ist besser; ±20-%-Trefferquote bei Kalorien: höher ist besser. Unterschiedliche Datensätze, unterschiedliche Eingaben — siehe unten.</figcaption>
</figure>

## 4 Fotos sind für jedes Modell schwierig

Enthält die Texteingabe bereits eine Mengenangabe („150 g“, „1 cup“), ist die getippte Eingabe nahezu gelöst. Kanonische Lebensmittel mit bekannter Gramm- oder Mengenangabe liefern meist exakte oder nahezu exakte Ergebnisse. Ein Barcode-Scan oder eine gespeicherte Mahlzeit mit fester Rezeptur verhält sich entsprechend.

Die Fotoschätzung ist ein anderes Problem. Ein Modell muss Portionsgröße, Tellerzusammensetzung und verborgene Zutaten (Öl, Dressing, Soße) aus einem zweidimensionalen Bild ohne Maßstabsreferenz ableiten. Das ist schwierig und in der Bildverarbeitungs-KI generell ungelöst — nicht spezifisch für Chompass oder einen einzelnen Anbieter.

In unseren Tests zeigte sich:

- Selbst das beste getestete Bezahl-Vision-Modell verfehlt bei rund jeder zweiten Mahlzeit die Kalorienangabe um mehr als 20 %.
- Die Schwierigkeit hängt nicht von der Mahlzeitgröße ab. Schwierige und einfache Foto-Kohorten weisen nahezu dieselben mittleren Ground-Truth-Kalorienwerte auf. Scheitern liegt an Portionsmaßstab und Dichte, die von der Kamera unterschätzt werden.
- Der dominante Fehlermodus ist die **Überschätzung im Restaurant-Portionsmaßstab**: Modelle unterstellen Teller- oder Beilagengrößen im Gastronomiestil, die nicht tatsächlich vorlagen. Weitere wiederkehrende Fehlermodi sind die **Unterschätzung verborgener Kalorien** (Öl, Tahini, ganzer Kuchen statt Stück) sowie **unübersichtliche Mehrkomponenten-Tabletts**, bei denen die Identifikation weitgehend korrekt, die Grammangabe jedoch falsch ist.
- Saubere Laboraufnahmen von oben sind nur geringfügig einfacher als Smartphone-Fotos von Mahlzeiten. Auf einer kleinen Nutrition5k-Teilmenge lag der WMAPE weiterhin bei etwa 35 % — weit entfernt von der Texteingabe mit Mengenangabe (~6 %). Unordentliche Smartphone-Fotos allein erklären die Lücke somit nicht.

<figure>
  <img src="/img/blog/accuracy/failure-modes.png" alt="Three cards: restaurant overestimate plus 100 to 200 percent kcal, hidden-calorie miss minus 65 to 80 percent kcal, and busy multi-item tray with grams wrong." width="800" height="450" loading="lazy">
  <figcaption>Konsens-Fehlermodi über fünf Vision-Modelle auf JFB 50. Schwierige Teller sind nicht einfach kalorienreiche Mahlzeiten.</figcaption>
</figure>

Wer präzise Zahlen benötigt, sollte eine getippte Eingabe **mit angegebener Menge**, einen Barcode-Scan oder eine gespeicherte Mahlzeit verwenden — nachweislich zuverlässiger als ein Foto allein.

### 4.1 Foto plus Kurznotiz ist keine „getippte Eingabe“

Wir haben dieselben 50 JFB-Mahlzeiten zusätzlich als **Bild + Nutzernotiz** getestet: entweder als Mahlzeittitel (z. B. `Breakfast Platter`) oder als Zutatenliste ohne Mengenangabe (z. B. `Rührei, Speck, Bratkartoffeln…`). Bei einem kostenlosen Gemma-Modell schnitt „nur Foto“ besser ab als beide Varianten — WMAPE **41,8 %** gegenüber **44,9 %** (Titel) und **45,8 %** (Zutatennamen).

Das widerspricht dem starken FNDDS-Textergebnis **nicht**. Diese Notizen enthalten keine Gramm- oder Mengenangaben; sie ähneln eher einer Bildunterschrift als der Angabe „150 g Hähnchen“. Dass ein solcher Text allein nicht an die mengenbasierte FNDDS-Texteingabe heranreicht, ist zu erwarten — wir haben bislang keine reine Text-Kontrollgruppe für JFB veröffentlicht, und eine Überprüfung von „Foto + Titel“ mit einem Bezahlmodell steht noch aus. Produktseitige Schlussfolgerung: Eine vage Notiz kann die Nutzerführung oder Identifikation unterstützen, ersetzt aber keine Mengenangabe.

## 5 Prompt-Verfeinerung, Tiefeninformation und Video lösen das Tellerproblem nicht

Nach A/B-Tests mehrerer Prompt-Varianten anhand derselben 50 Mahlzeitfotos blieb der WMAPE für Tellerfotos im Bereich von etwa **33–45 %**. Kompakte Prompts erreichten oder übertrafen häufig längere, „produktionsreife“ Formulierungen. Kurze Regeln zur Verankerung der Portionsgröße oder zur Vermeidung erfundener Beilagen veränderten den schwierigen Bereich der Verteilung kaum. Ein expliziter Maßstabs-Anker im Prompt (Referenzgröße Teller/Schale) verbesserte den WMAPE um rund **1,5 Prozentpunkte** — real, aber zu gering, um allein produktiv eingesetzt zu werden.

Die Modellwahl wirkt stärker als die Prompt-Feinabstimmung. Bezahlte Vision-Modelle erreichen einen WMAPE von etwa 32 %; kostenlose Modelle bleiben bei etwa 40 %. Keines der beiden erreicht die getippte Eingabe mit Mengenangabe.

<figure>
  <img src="/img/blog/accuracy/plate-model-ladder.png" alt="Horizontal bar chart of plate photo WMAPE by model from Gemini 3.6 Flash at 32.3 percent down to GPT-5 Nano at 43.8 percent, with a dashed typed-entry reference line at 5.7 percent." width="800" height="450" loading="lazy">
  <figcaption>WMAPE für Tellerfotos nach Modell (JFB 50, kompakter Prompt). Die gestrichelte Linie zeigt den WMAPE der getippten Eingabe mit Mengenangabe (FNDDS) — eine andere Aufgabe, nur zum Größenvergleich dargestellt.</figcaption>
</figure>

Zusätzlich wurden erweiterte Erfassungshinweise anhand von Nutrition5k-Laborclips geprüft:

- **Monokulare Tiefen-/Volumenschätzung** aus einem Einzelfoto erreichte für die Massenschätzung nicht die erforderliche Genauigkeit.
- **Natives Video** (Übermittlung des Drehteller-Clips als `video_url` anstelle eines Einzelbilds, gleiches kostenloses Gemma-Modell, 12 gepaarte Gerichte) schnitt **schlechter** ab als das Einzelbild: WMAPE **25,6 % → 37,2 %**, ±20-%-Trefferquote bei Kalorien **41,7 % → 33,3 %**, dabei etwa **4,2-fach** höherer Prompt-Token-Verbrauch und schlechtere Zuverlässigkeit im kostenlosen Tarif. Dieser Ansatz ist vorerst zurückgestellt — der Test beruhte auf einem festen Labor-Drehteller, keiner freihändigen Smartphone-Rundumaufnahme, doch „einfach mehr Einzelbilder liefern“ half auf dieser Datenbasis nicht.

## 6 Was tatsächlich etwas gebracht hat

Zwei Ergebnisse haben die Weiterentwicklung der App unmittelbar beeinflusst.

**Schlankere Produktions-Prompts.** Der bisherige Text-Prompt lieferte für vorgeschlagene Portionsangaben (z. B. „2 Scheiben“) nicht zuverlässig das Feld `grams_per_unit`. Ohne dieses Feld verwarf die App KI-Portionseinheiten bei praktisch jeder Textantwort stillschweigend. Ein überarbeiteter Prompt erhält dieselbe Makronährstoff-Genauigkeit bei etwa halber Prompt-Länge und liefert bei **40 von 41** Evaluationseinträgen verwendbare Einheiten. Diese Formulierung ist heute produktiv im Einsatz.

**Portionsklärung (in Entwicklung).** In einer simulierten Evaluation senkte das Einspeisen einer Ground-Truth-Portionsangabe in den Foto-Prompt — als Platzhalter für eine per Antipp auszulösende Rückfrage „Wie viel lag auf dem Teller?“ — den WMAPE für Fotos um **15 Prozentpunkte** (35,9 % → 22,8 %) und erhöhte die ±20-%-Trefferquote um 12 Punkte auf demselben 50-Foto-Datensatz. Deshalb ist eine Portions-Rückfrage-Funktion (Chip-UI) der nächste Entwicklungsschritt: Der Nutzer wird nach dem Maßstab gefragt, anstatt darauf zu hoffen, dass das Modell ihn errät. Das bestätigt die Erkenntnis aus der Texteingabe — **die Mengenangabe in der Eingabe** ist entscheidend für die Makronährstoffgenauigkeit, ob als getippte Grammangabe oder als angetippter Chip.

<figure>
  <img src="/img/blog/accuracy/portion-clarify.png" alt="Grouped bars showing photo-only versus photo plus simulated portion answer: WMAPE 35.9 to 22.8 percent, and within ±20 percent calories 40 to 50 percent." width="800" height="450" loading="lazy">
  <figcaption>Simulierte Portionsklärung auf JFB 50 (Gemini 3.5 Flash-Lite). Die Orakel-Portionsangabe steht stellvertretend für eine Antipp-Rückfrage — daher der nächste Entwicklungsschritt.</figcaption>
</figure>

## 7 Handlungsempfehlungen für Nutzer

- Bevorzugt getippten Text **mit Gramm- oder Mengenangabe**, Barcode-Scan oder eine gespeicherte Mahlzeit, wenn genaue Werte wichtig sind.
- Ein Foto mit Mahlzeittitel oder Zutatenliste ohne Mengenangabe bleibt im Wesentlichen eine Fotoschätzung.
- Behandelt reine Fotoeingaben (oder Foto + vage Notiz) als schnellen Entwurf, nicht als gewogene Mahlzeit.
- Da Chompass dem BYOK-Prinzip folgt, richtet sich die Genauigkeit nach dem gewählten Modell; diese Werte sind modellspezifische Testergebnisse, keine einzelne „Chompass-Genauigkeit“.

## 8 Einschränkungen

- Es handelt sich um Offline-Testrahmen-Werte auf kleinen, festen gelabelten Datensätzen — kein Live-Produktionsmonitoring der Genauigkeit. Ergebnisse variieren je nach Modell, Fotoqualität und Lebensmittelart.
- Die starken Textergebnisse beruhen auf FNDDS-Strings mit Mengenangabe; die Foto- bzw. Bild+Notiz-Werte beruhen auf angerichteten JFB-Mahlzeiten. Sie sind nicht als dieselbe, nur zweimal erfasste Mahlzeit zu lesen.
- On-Device-Gemma 4 (Android, opt-in) ist kleiner als Cloud-Modelle und in der Regel weniger genau.
- Die Werte ändern sich mit neuen Modellen und Prompts. Dieser Beitrag spiegelt den im [Benchmark-Status-Dokument](https://codeberg.org/fitguy/chompass/src/branch/main/docs/FOOD_ACCURACY_BENCHMARK_STATUS.md) datierten Stand wider (Ende Juli 2026).
