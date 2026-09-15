## What and why

<!-- One short paragraph: what changes and why. Link the issue with "Closes #N" if one exists. -->

Opening this pull request licenses your contribution under the project's [MIT license](LICENSE).

## Checklist

- [ ] `cd android && ./gradlew test` passes
- [ ] **Parity**: if this touches nutrition formulas, diary export/import JSON, meal sharing, or Health Connect, the Android app (`android/`) and the web domain mirror (`web/app/src/lib/chompass-core/`) changed together, and `devenv tasks run release:check-parity` passes
- [ ] User-facing strings live in `res/values/strings.xml` (never hardcoded in Compose); locale files updated when text changed (16 locales, `docs/LOCALIZATION.md`)
- [ ] New network calls: endpoint + on/off default stated in the PR; `docs/PRIVACY.md` network table updated
- [ ] No user content (search queries, food names, API keys) written to logs
- [ ] No analytics, ads, Google Play services, or new dependencies without prior discussion
- [ ] `docs/CHANGELOG.md` entry added if the change is user-visible
