---
title: Wie genau ist KI-gestütztes Food-Logging?
date: 2026-07-28
description: Tierliste der Eingabemethoden nach Benchmark-Genauigkeit — Text mit Menge oben, Tellerfotos ohne Maßstab unten. Gemessen an gelabelten Datensätzen.
draft: true
---

## 1 Einleitung

Chompass folgt dem BYOK-Prinzip: Nutzer bringen einen eigenen Cloud-KI-Schlüssel mit (oder betreiben Gemma 4 lokal auf dem Android-Gerät). Die Genauigkeit der Lebensmittelanalyse hängt daher überwiegend vom gewählten Modell ab, nicht von einer Chompass-spezifischen Eigenschaft. Wir messen die Genauigkeit gegen gelabelte Datensätze und veröffentlichen die Zahlen, anstatt eine einzelne Genauigkeitsangabe zu behaupten.

## 2 Methodik

Alle nachfolgenden Werte stammen aus einem Offline-Testrahmen (Research Harness) gegen gelabelte Datensätze mit bekannten Ground-Truth-Kalorien- und Makronährstoffwerten. Die vollständige Methodik, alle Testläufe und die Rohdatentabellen sind im [Benchmark-Status-Dokument](https://codeberg.org/fitguy/chompass/src/branch/main/docs/FOOD_ACCURACY_BENCHMARK_STATUS.md) auf Codeberg hinterlegt. Eine kürzere Zusammenfassung findet sich in [ACCURACY.md](https://codeberg.org/fitguy/chompass/src/branch/main/docs/ACCURACY.md).

WMAPE = gewichteter mittlerer absoluter prozentualer Fehler über Kalorien, Protein, Kohlenhydrate und Fett. **Niedriger ist besser.** ±20 % = Anteil der Einträge, deren Kalorien innerhalb ±20 % der Ground Truth liegen (**höher ist besser**). Die Werte stammen aus Food-Analyse-Prompts, die dem in der App ausgelieferten Prompt entsprechen (bzw. simulierten Portionsantworten, wo gekennzeichnet).

**Wichtig:** Unterschiedliche Zeilen nutzen unterschiedliche Datensätze und Modelle — die Tierliste ordnet **Eingabearten** nach typischer Makrogenauigkeit, nicht denselben 50 Tellern zweimal gemessen. Details und Rohdaten: Statusdokument.

## 3 Tierliste der Eingaben

| Tier | Eingabe | WMAPE | ±20 % kcal | Benchmark |
|---|---|---:|---:|---|
| **S** | Text **mit** Gramm oder Haushaltsmaß (`150 g`, `2 tbsp`, `1 cup`) | **~5–6 %** | **~90–93 %** | FNDDS 42 (Gemma free / Flash Lite) |
| **S** | Text mit Haushaltsmaß allein (Slice des realistischen Text-Sets) | **4,3 %** | **92 %** | Realistic text 12 (Flash Lite) |
| **S\*** | Barcode-Scan / gespeicherte Mahlzeit mit fester Rezeptur | ≈ Text mit Menge | — | Produktanalogie; keine eigene Foto-Bench |
| **A** | Markenname **mit** Datenbank-Auflösung (OFF/USDA, grounded — **WIP**, nicht in der App) | **18,5 %** gesamt / **~7 %** Marken | **76 %** | Realistic text 38 (Flash Lite + OFF-Fixtures) |
| **A** | Foto + **genaue** Portionsangabe (simulierte Chip-Antwort: Gramm / Mengenangaben) | **22,8 %** | **50 %** | JFB 50, Flash Lite (Orakel) |
| **B** | Foto + **vage** Mengenangabe in der Notiz (`large plate`, `a couple eggs` — ohne exakte Grammzahl) | **25,3 %** | **52 %** | JFB 50, Flash Lite (Lq) |
| **B** | Realistischer Text **ohne** Gramm (Titel, Multi, Marke gemischt) | **27,3 %** | **71 %** | Realistic text 38, Flash Lite ungrounded |
| **B** | Vager Texttitel ohne Menge (`Chicken breast, roasted`) | **22,6 %** | **58 %** | Realistic text 12 (Flash Lite) — oft falsche Portionsannahme |
| **B** | Foto + qualitative Größen-Chips (`small` / `regular` / `large`) | **28,7 %** | **32 %** | Nutrition5k 50, Flash Lite |
| **C** | Nur Foto — bestes getestetes Bezahlmodell | **32,3 %** | **50 %** | JFB 50, Gemini 3.6 Flash |
| **C** | Foto + Mahlzeittitel ohne Menge (`Breakfast Platter`) | **33,0 %** | **36 %** | JFB 50, Flash Lite (L1) |
| **C** | Nur Foto — Flash Lite | **35,9 %** | **40 %** | JFB 50 |
| **D** | Nur Foto — kostenloses / on-device-taugliches Modell | **~40 %** | **~32 %** | JFB 50, Gemma free |
| **F** | Nur Markenname ohne Datenbank (`Nutella`, `Coca-Cola`) | **~120 %** | **63 %** | Realistic text 8, Flash Lite ungrounded |

\*Barcode und gespeicherte Mahlzeiten verhalten sich wie Nachschlagen mit bekannter Portionsgröße — deshalb Tier S —, wurden aber nicht als eigene Vision-Zeile gegen JFB gemessen.

**Kurzformel:** Menge in der Eingabe (Gramm, Haushaltsmaß, Chip, oder wenigstens vage Quantität) entscheidet über das Tier. Identität allein (Titel, Marke, Foto ohne Maßstab) landet deutlich tiefer.

<figure>
  <img src="/img/blog/accuracy/text-vs-photo.png" alt="Two bar charts comparing portioned typed entry, best paid photo, and free photo: WMAPE 5.7% vs 32.3% vs 39.8%, and within ±20% calories 90% vs 50% vs 32%." width="800" height="450" loading="lazy">
  <figcaption>Auszug Tier S vs. Tier C/D: Text mit Menge (FNDDS 42) gegen Tellerfotos (JFB 50). Unterschiedliche Datensätze — siehe Tierliste.</figcaption>
</figure>

## 4 Fotos sind für jedes Modell schwierig

Enthält die Texteingabe bereits eine Mengenangabe („150 g“, „1 cup“), ist die getippte Eingabe nahezu gelöst. Kanonische Lebensmittel mit bekannter Gramm- oder Mengenangabe liefern meist exakte oder nahezu exakte Ergebnisse. Ein Barcode-Scan oder eine gespeicherte Mahlzeit mit fester Rezeptur verhält sich entsprechend.

Die Fotoschätzung ist ein anderes Problem. Ein Modell muss Portionsgröße, Tellerzusammensetzung und verborgene Zutaten (Öl, Dressing, Soße) aus einem zweidimensionalen Bild ohne Maßstabsreferenz ableiten. Das ist schwierig und in der Bildverarbeitungs-KI generell ungelöst — nicht spezifisch für Chompass oder einen einzelnen Anbieter.

In unseren Tests zeigte sich:

- Selbst das beste getestete Bezahl-Vision-Modell verfehlt bei rund jeder zweiten Mahlzeit die Kalorienangabe um mehr als 20 %.
- Die Schwierigkeit hängt nicht von der Mahlzeitgröße ab. Schwierige und einfache Foto-Kohorten weisen nahezu dieselben mittleren Ground-Truth-Kalorienwerte auf. Scheitern liegt an Portionsmaßstab und Dichte, die von der Kamera unterschätzt werden.
- Der dominante Fehlermodus ist die **Überschätzung im Restaurant-Portionsmaßstab**: Modelle unterstellen Teller- oder Beilagengrößen im Gastronomiestil, die nicht tatsächlich vorlagen. Weitere wiederkehrende Fehlermodi sind die **Unterschätzung verborgener Kalorien** (Öl, Tahini, ganzer Kuchen statt Stück) sowie **unübersichtliche Mehrkomponenten-Tabletts**, bei denen die Identifikation weitgehend korrekt, die Grammangabe jedoch falsch ist.
- Saubere Laboraufnahmen von oben sind nur geringfügig einfacher als Smartphone-Fotos von Mahlzeiten. Auf einer kleinen Nutrition5k-Teilmenge lag der WMAPE weiterhin bei etwa 35–37 % — weit entfernt von der Texteingabe mit Mengenangabe (~6 %). Unordentliche Smartphone-Fotos allein erklären die Lücke somit nicht.

<figure>
  <img src="/img/blog/accuracy/failure-modes.png" alt="Three cards: restaurant overestimate plus 100 to 200 percent kcal, hidden-calorie miss minus 65 to 80 percent kcal, and busy multi-item tray with grams wrong." width="800" height="450" loading="lazy">
  <figcaption>Konsens-Fehlermodi über fünf Vision-Modelle auf JFB 50. Schwierige Teller sind nicht einfach kalorienreiche Mahlzeiten.</figcaption>
</figure>

Wer präzise Zahlen benötigt, sollte eine getippte Eingabe **mit angegebener Menge**, einen Barcode-Scan oder eine gespeicherte Mahlzeit verwenden — nachweislich zuverlässiger als ein Foto allein.

### 4.1 Foto plus Kurznotiz ist keine „getippte Eingabe“

„Foto + Notiz“ ist nicht dasselbe wie „150 g Hähnchen“. Die Notiz enthält keine Gramm- oder Mengenangabe; sie liefert höchstens eine **Identitätshilfe**. Ob das Makros verbessert, hängt von Modell, Datensatz und Notizqualität ab — nicht von einer allgemeinen Regel.

**JFB (50 reale Handyfotos), kostenloses Gemma:** dieselben Mahlzeiten als Bild allein, Bild + Mahlzeittitel (`Breakfast Platter`) oder Bild + Zutatennamen ohne Menge. „Nur Foto“ war am besten — WMAPE **41,8 %** gegenüber **44,9 %** (Titel) und **45,8 %** (Zutatennamen). Vage Bildunterschriften halfen hier nicht.

**Nutrition5k und ACETADA (je n=15), Gemini 3.5 Flash-Lite:** mit **spezifischen Zutaten- bzw. Lebensmittel-Namen** (ohne Mengen) verbesserte sich die Schätzung. Nutrition5k: WMAPE **37,4 % → 30,6 %**. Auf dem Forschungsdatensatz ACETADA (CC BY-NC, nur Forschung — keine Produktclaims): Bild allein **22,7 %** / ±20 % **40 %**; mit Mahlzeittyp **18,9 %** / **67 %**; mit benannten Lebensmitteln **15,0 %** / **87 %**. Rohdaten: [Benchmark-Status](https://codeberg.org/fitguy/chompass/src/branch/main/docs/FOOD_ACCURACY_BENCHMARK_STATUS.md) (Abschnitt Image + description on Nutrition5k / ACETADA).

Die Botschaft bleibt: **Identität ohne Maßstab ist kein Mengen-Logging.** Eine präzise Zutatenliste kann dem Modell helfen, *was* auf dem Teller liegt; sie ersetzt keine Gramm-, Chip- oder Haushaltsmaßangabe für *wie viel*. Selbst der beste Kurznotiz-Lauf (ACETADA L2, Forschung) liegt weit über dem FNDDS-Text mit Menge (~6 % WMAPE).

**Nachtrag (2026-07-30):** In der Tierliste (§3) ist **Foto + vage Mengenangabe** (Lq) Tier **B** — klar besser als nur Foto (Tier C). Nur der Mahlzeittitel (L1) bleibt in Tier C. Qualitative Größen-Chips landen ebenfalls in B, schlagen Lq aber nicht. Details: [Benchmark-Status](https://codeberg.org/fitguy/chompass/src/branch/main/docs/FOOD_ACCURACY_BENCHMARK_STATUS.md) (Abschnitt Photo-adjacent entry matrix).

### 4.2 Auch reiner Text ohne Grammangabe ist eine andere Aufgabe

Um die Lücke zwischen „FNDDS mit Gramm“ und „vager Mahlzeitnotiz“ direkt zu messen, haben wir denselben Flash-Lite-Pfad auf einem **realistischen Text-Set** (38 Einträge) laufen lassen: Mahlzeit- oder Produktnamen **ohne Grammzahl in der Eingabe** — vage Titel (`Chicken breast, roasted`), Haushaltsmaße (`2 tbsp peanut butter`), Mehrkomponenten-Mahlzeiten und Markennamen (`Nutella`, `Coca-Cola`). Die Ground-Truth-Grammwerte bleiben nur in der Auswertung, nicht im Prompt. Methodik und Slice-Aufschlüsselung: [Benchmark-Status](https://codeberg.org/fitguy/chompass/src/branch/main/docs/FOOD_ACCURACY_BENCHMARK_STATUS.md) (Abschnitt Grounded / realistic text).

| Eingabe (Flash Lite, ungrounded) | WMAPE | Innerhalb ±20 % kcal | n |
|---|---|---|---|
| **FNDDS-Text mit Gramm/Maß** (wie in §3) | **~5–6 %** | **~90–93 %** | 42 |
| **Realistischer Text ohne Gramm** (gesamt) | **27,3 %** | **71 %** | 38 |
| davon Markennamen | ~120 % | 63 % | 8 |
| davon Haushaltsmaße (`2 tbsp`, `1 cup`, …) | **4,3 %** | **92 %** | 12 |
| davon vage Titel | 22,6 % | 58 % | 12 |

Sobald die Menge im Text steht (Haushaltsmaß oder Gramm), bleibt die Schätzung stark. Fehlt sie — oder steht nur ein Markenname —, driftet dasselbe Modell weit ab. Das passt zu §4.1: Identität allein schließt die Portionslücke nicht; eine Mengenangabe in der Eingabe schon.

Für Packungswaren hilft eine **verifizierte Datenbank** (Barcode / Open Food Facts) klar mehr als reine Modellschätzung. Ein optionaler „Grounded“-Pfad, der Markennamen gegen OFF/USDA auflöst und Makros nur aus Datenbankzeilen skaliert, lag im gleichen realistischen Text-Lauf klar vor ungrounded Flash Lite (WMAPE **18,5 %** vs. **27,3 %**; Marken-Slice **7 %** vs. ~120 %). Dieser Pfad ist **Forschung / WIP** und in der App noch nicht freigeschaltet — die Zahlen belegen die Richtung, kein ausgeliefertes Feature.

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

**Portionsklärung (in Entwicklung).** In einer simulierten Evaluation senkte das Einspeisen einer Ground-Truth-Portionsangabe in den Foto-Prompt — als Platzhalter für eine per Antipp auszulösende Rückfrage „Wie viel lag auf dem Teller?“ — den WMAPE für Fotos um **15 Prozentpunkte** (35,9 % → 22,8 %) und erhöhte die ±20-%-Trefferquote um 12 Punkte auf demselben 50-Foto-Datensatz. Deshalb ist eine Portions-Rückfrage-Funktion (Chip-UI) der nächste Entwicklungsschritt: Der Nutzer wird nach dem Maßstab gefragt, anstatt darauf zu hoffen, dass das Modell ihn errät. Das bestätigt die Erkenntnis aus der Texteingabe — **die Mengenangabe in der Eingabe** ist entscheidend für die Makronährstoffgenauigkeit, ob als getippte Grammangabe oder als angetippter Chip. Spezifische Zutatennamen (§4.1) können die Identifikation stützen; die große Stellschraube bleibt die Portions-UX.

<figure>
  <img src="/img/blog/accuracy/portion-clarify.png" alt="Grouped bars showing photo-only versus photo plus simulated portion answer: WMAPE 35.9 to 22.8 percent, and within ±20 percent calories 40 to 50 percent." width="800" height="450" loading="lazy">
  <figcaption>Simulierte Portionsklärung auf JFB 50 (Gemini 3.5 Flash-Lite). Die Orakel-Portionsangabe steht stellvertretend für eine Antipp-Rückfrage — daher der nächste Entwicklungsschritt.</figcaption>
</figure>

## 7 Handlungsempfehlungen für Nutzer

- **Tier S:** getippter Text **mit** Gramm oder Haushaltsmaß, Barcode oder gespeicherte Mahlzeit — wenn genaue Werte wichtig sind.
- **Tier A/B:** Foto plus Portionschip bzw. Notiz **mit** Mengensprache (auch vage: „große Portion“, „zwei Eier“) schlägt Titel-only und Foto allein.
- **Tier C/D:** Nur Foto oder Foto + Mahlzeittitel ohne Menge = schneller Entwurf, keine gewogene Mahlzeit.
- **Tier F:** Nur Markenname ohne Scan/Datenbank driftet stark — lieber Barcode oder Grammangabe.
- Da Chompass dem BYOK-Prinzip folgt, richtet sich die Genauigkeit nach dem gewählten Modell; die Tierliste ist eingabe- und modellspezifisch, keine einzelne „Chompass-Genauigkeit“.

## 8 Einschränkungen

- Es handelt sich um Offline-Testrahmen-Werte auf kleinen, festen gelabelten Datensätzen — kein Live-Produktionsmonitoring der Genauigkeit. Ergebnisse variieren je nach Modell, Fotoqualität und Lebensmittelart.
- Die starken Textergebnisse beruhen auf FNDDS-Strings mit Mengenangabe; die Foto-Werte auf JFB; Bild+Notiz zusätzlich auf Nutrition5k und (nur Forschung) ACETADA; das realistische Text-Set (§4.2) lässt die Grammangabe bewusst weg. Sie sind nicht als dieselbe, nur zweimal erfasste Mahlzeit zu lesen.
- ACETADA-Zahlen sind **CC BY-NC** und nur für Forschung — nicht für kommerzielle Genauigkeitsangaben.
- On-Device-Gemma 4 (Android, opt-in) ist kleiner als Cloud-Modelle und in der Regel weniger genau.
- Die Werte ändern sich mit neuen Modellen und Prompts. Dieser Beitrag spiegelt den im [Benchmark-Status-Dokument](https://codeberg.org/fitguy/chompass/src/branch/main/docs/FOOD_ACCURACY_BENCHMARK_STATUS.md) datierten Stand wider (Ende Juli 2026).
