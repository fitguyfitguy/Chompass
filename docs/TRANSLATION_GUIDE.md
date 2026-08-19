# Chompass translation guide

How to translate Chompass into one of the 18 supported locales, and how a
full pack gets reviewed before it ships. Written for community translators
and for maintainers reviewing translation merges.

Mechanics (keys, locale contract, the verbatim-EN-copy checker) live in
[`LOCALIZATION.md`](LOCALIZATION.md). The house voice and the AI-ism ban
lists live in `UI_COPY_STYLE.md` (§2 to §8). This guide
is the entry point: it tells you what to translate, how to write it, and
how to prove it fits.

## 1. Parent languages: English and German

Chompass has **two parent languages**, with different roles:

| Language | Role |
|----------|------|
| **English** | **Semantic parent.** The meaning source. Every translation is written from the English string in `values/strings.xml` (Android) or `catalogs/en.js` (PWA). |
| **German** | **Fit + voice parent.** The model for how a full pack reads and fits. German is the first complete, style-reviewed pack: informal du voice, plain language, compact labels that fit the fixed-width budget. |

Take either as your working parent:

- **English as parent** is the default: translate the English meaning into
  your language.
- **German as parent** is a good second reference: when an English string
  is ambiguous or a label is tight, look at how the German pack resolved
  it. German shows the house voice in practice.

**German is never a pivot for meaning.** You translate the English meaning,
not the German text. German models voice and fit; it does not supply
meaning. If English and German disagree, English wins.

**Russian is not a reference language at this time.** The Russian pack is
complete but has not had the style review German had. Do not copy Russian
solutions into your pack.

## 2. House voice

Chompass copy is **neutral, compact, factual**. It states what the app does
and what the user can do. It never performs.

- No marketing voice: no "unlock", "supercharge", "elevate", "empower",
  "seamlessly", "effortlessly". State the function.
- No hype or absolutes: "never", "always", "completely", "finally".
- No exclamation marks in UI strings.
- No formal connectors: "additionally", "furthermore", "note that".
- No emdashes (—). Numeric ranges stay in words in English ("3 to 4
  times"); German keeps its own convention (en-dash, DIN 5008, §3).
- At most one parenthetical per string.
- State the result, not the mechanism: "Logging no longer freezes after a
  quick save", not "All 58 state writes now go through atomic update".
- One idea per string. Subject and verb up front, active voice. Common
  words before formal ones.
- Buttons say what they do: "Save", "Delete", "Log". Not "Proceed",
  "Confirm action".
- Errors say what happened and what to do: "Could not reach the server.
  Check your connection and try again." Never "An unexpected error
  occurred".

The full rules, the AI-ism ban list, and the string budgets are in
`UI_COPY_STYLE.md` §2 to §5. The ban lists apply in
**every** locale: a German string with "nahtlos" is as AI as an English one
with "seamless". Keep a per-locale ban list in the same shape as §5 and §7
of that guide.

## 3. German specifics

German is the fit + voice parent, so its rules matter for the whole model:

- **Informal du.** The app addresses the user as "du", never "Sie".
  "Sehen Sie" → "Sieh dir", "Ihren" → "deinen".
- **DIN ISO 24495-1:2024-03** (Einfache Sprache) is the German yardstick:
  short sentences, common words, one idea per sentence, active voice.
- **En-dashes in numeric ranges are correct German** (DIN 5008): "3–4 Mal
  pro Woche". The em-dash stays banned everywhere.
- **German AI tells to avoid**: "Es ist wichtig zu beachten", "In der
  heutigen schnelllebigen Welt", "nahtlos", "fesselnd", "Tauchen wir ein
  in", "Zusammenfassend lässt sich sagen", "darüber hinaus" as an opener,
  English nouns where a German word exists ("Feature", "Update",
  "Release"). Full list: `UI_COPY_STYLE.md` §7.

## 4. Compact labels: the fit budget

Some strings render in **fixed-width UI**: chips, tabs, status lines,
buttons, meal slots, bottom-nav labels, widget cards. Long translations
truncate or overflow there. The registry
[`testdata/parity/compact_strings.json`](../testdata/parity/compact_strings.json)
lists these keys and their budgets:

- **Budget:** value ≤ 12 Latin characters. CJK glyphs are narrow and count
  at 0.5 (pure-CJK strings cap at 8 characters).
- **Per-key overrides** widen the budget where the element has more room:
  macro status lines get 20 (they include the value), bottom-nav labels
  fit 14, full-width sheet rows fit 16.
- **Placeholder position:** `valueFirst` keys (macro status lines) must put
  the first placeholder before the first word: EN "10,7g over" → DE
  "10,7g drüber", NOT "drüber 10,7g".
- **Guidance:** a short colloquial form beats the full word where the
  element is tight: drüber, 1W, Start, Mittag, Erneut, Eigene. Prose keys
  are simply not in the registry.

**Fit rule:** if a compact label fits in German (the worst-case
word-inflation locale, 30 to 40% longer than English, up to 2x on short
strings), it fits in the other Latin-script packs. Use German as your
yardstick for tight labels.

## 5. Length collisions: keep distinct actions distinct

Two different keys that render in the same context must not collapse to
the same translated string. Example from the German review:
`action_clear` → "Löschen" duplicated `action_delete`; the pack fixed it
by translating `action_clear` as "Leeren".

Before submitting, scan your pack for **same-value pairs** among keys that
appear on the same screen (buttons, menu items, tabs). If two actions
would look identical, give one a different word. The merge review checks
this.

## 6. UI warnings: the rendered worst-case tripwire

The compact gate warns when a placeholder key's value plus the worst-case
value ("91,5" + "g") exceeds the status-line budget at max font scale
(ru "91,5g осталось" = 14 > 13). A warning is a **tripwire, not a pass**:

- Prefer a shorter suffix: "übrig" over "осталось".
- Accept the auto-shrink only when the value is genuinely long and no
  shorter word says the same thing.

Run the gate and read its warnings before submitting (§10).

## 7. Placeholders and plurals

- **Numbered placeholders keep their position.** Use `%1$s`, `%2$d` so the
  translator can reorder: "%1$d kcal left" becomes natural in every
  locale. Never embed a value into the middle of a literal string from
  code.
- **Complete sentences, never fragments.** One key per complete sentence
  or self-contained phrase, with placeholders inside it. A sentence built
  from concatenated keys cannot be translated.
- **Pluralization is per locale.** "1 item(s)" is not a translation, it is
  a bug. Android plurals exist so that ru, uk, and ar (3+ plural forms)
  get real forms. When a countable string is added, all plural forms ship
  from day one.
- **Full locale codes.** The contract uses `pt-BR` and `zh-CN`;
  language-only codes lose region rules.

## 8. Term dictionary

Same term, same thing, in every locale:

- **Log** is a verb and a tab; **entry** is the saved item. Pick the pair
  and keep it.
- **Protein** is the established German term (not "Eiweiß"). Other locales
  keep their own established term once chosen.
- **Units stay as loanwords**: kcal, g, kg. "Calories" is fine in prose,
  kcal in numbers.
- **Menu and settings names are translated once and then kept exact** in
  that locale. Users report bugs using the names they see.
- **AI features say "AI" plainly.** Copy says what the feature does; the
  privacy story is stated where the user can verify it, not sprinkled as
  flavor text.

## 9. What not to translate

- **Verbatim EN copies** (hard rule, enforced by the checker): a
  translated string identical to English is a gap, not a translation.
  Formats, URLs, and bare units/loanwords are exempt.
- **Machine IDs and export JSON field names** stay in English.
- **Dates, numbers, and units** display in the app locale; export and
  protocol formats stay `Locale.US`.
- **No text baked into images.** Labels live in strings, not in icons or
  illustrations.

## 10. Validation

Run these before submitting a pack:

```bash
# Android resource completeness + compact-label gate (strict = fail on violations)
uv run python scripts/check_android_strings.py --strict-compact

# Full parity: locale fixture + PWA i18n tests + contract schemas
devenv tasks run release:check-parity
```

The PWA compact-label test lives in
`web/app/src/lib/__tests__/i18n.test.js` and uses the same registry.

**Screenshot fit tests:** locale-fit references (de + ru variants of the
compact screens: Home over-goal macros, Progress range chips, Saved Meals
tabs, Settings hub, plus a ru Home at `fontScale = 1.3`) live under
`android/app/src/screenshotTestDebug/reference/`. After intentional label
changes, regenerate with `./gradlew :app:updateDebugScreenshotTest` and run
`validateDebugScreenshotTest` to catch regressions.

## 11. Full-pack contribution workflow

A full pack (a community translation merge) needs a **UI-fit pass** before
it ships:

1. Translate from the English meaning (§1), in the house voice (§2), with
   the per-locale ban list applied.
2. Render the compact screens (Home macro cards, Progress range chips,
   Saved Meals tabs, Settings hub, bottom nav) at max font scale in your
   locale and confirm nothing truncates or overflows (§4).
3. Check for intra-locale collisions (§5) and read the gate's warnings
   (§6).
4. Run `check_android_strings.py --strict-compact` and
   `release:check-parity` (§10).
5. Regenerate the de/ru screenshot references if the labels changed.

The merge review checks: voice (informal du for German, per-locale ban
lists), terms (§8), compact fit (§4), collisions (§5), placeholder and
plural integrity (§7), and zero verbatim EN copies (§9).

## 12. Checklist

- [ ] Written from the English meaning; German used only as fit + voice
      model, never as pivot
- [ ] House voice: neutral, compact, factual; no AI tells, no hype, no
      exclamation marks, no emdashes
- [ ] Per-locale ban list applied (German: §3; others: same shape)
- [ ] Compact labels within budget; German used as the fit yardstick
- [ ] No two distinct actions render as the same label (collision check)
- [ ] Rendered worst-case warnings read and acted on (shorter suffix or
      conscious accept)
- [ ] Numbered placeholders keep their position; plural forms complete
- [ ] Terms consistent with the dictionary (§8); menu/settings names exact
- [ ] No verbatim EN copies; machine IDs and export field names in English
- [ ] `check_android_strings.py --strict-compact` passes
- [ ] `release:check-parity` passes; screenshot references regenerated if
      labels changed