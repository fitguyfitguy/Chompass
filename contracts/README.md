# Wire-format contracts (Android ↔ PWA)

Versioned JSON Schemas for cross-app interchange. Fixtures in
[`testdata/parity/`](../testdata/parity/) must validate against these.

| Schema | Format | Consumers |
|--------|--------|-----------|
| [`diary-1.2.schema.json`](diary-1.2.schema.json) | Diary export `format_version` **1.2** | `DiaryExporter` / `DiaryImporter`, `web/.../diary-format.js` |
| [`diary-1.1.schema.json`](diary-1.1.schema.json) | Diary export **1.1** (legacy; still accepted on import) | same |
| [`body-metrics-1.0.schema.json`](body-metrics-1.0.schema.json) | Body metrics `kind=body_metrics` **1.0** | `BodyMetricsExporter` / `BodyMetricsImporter`, `body-metrics-format.js` |
| [`meal-share-v2.schema.json`](meal-share-v2.schema.json) | Meal share payload `v` **2** | `MealShare.kt`, `web/.../meal-share.js` |
| [`meal-share-v1.schema.json`](meal-share-v1.schema.json) | Meal share `v` **1** (legacy; still accepted on import) | same |
| [`sync-1.1.schema.json`](sync-1.1.schema.json) | User-hosted sync `kind=sync` **1.1** | `SyncDocument` / `SyncRepository`, `web/.../sync-format.js` |
| [`sync-1.0.schema.json`](sync-1.0.schema.json) | Sync **1.0** (legacy; still accepted on import) | same |

## New in diary 1.2 / sync 1.1 / meal-share v2

Item / food-record fields (diary & sync use **snake_case**; meal-share keeps **camelCase**):

| Diary / sync | Meal-share | Purpose |
|--------------|------------|---------|
| `serving_unit_options` | `servingUnitOptions` | `[{unit, grams_per_unit\|gramsPerUnit, quantity?}]`: enough to reconstruct grams |
| `selected_serving_unit` | `selectedServingUnit` | Active non-gram unit id |
| `selected_serving_quantity` | `selectedServingQuantity` | Quantity in that unit |
| `constituents` | `constituents` | Embedded multi-row meal breakdown on **one** diary entry (not separate rows). Empty `[]` = indivisible food. |

Constituent rows carry macros + `quantity_g` / `servingSizeGrams`, optional emoji, and the same serving-unit trio.

Schemas are a **structural / version** guard (`format_version` / `v` consts, required roots).
Root `additionalProperties: true` is intentional: clients may carry non-interchange
fields without breaking the other app. Field completeness is enforced by **strict
importers** plus **goldens** under `testdata/parity/`, not by locking every property
in the schema.

Bump the version constant **and** add a new schema file when the wire shape
changes. Run `devenv tasks run release:check-parity` (or
`./scripts/check_parity.sh`) before releasing.
