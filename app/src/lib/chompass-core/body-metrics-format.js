// @ts-check
/**
 * Serializer/validator for the body-metrics JSON export shape, compatible
 * with android/app/src/main/java/app/chompass/export/
 * BodyMetricsExporter.kt and BodyMetricsImporter.kt.
 */

export const BODY_METRICS_FORMAT_VERSION = "1.0";
export const BODY_METRICS_KIND = "body_metrics";

/**
 * Deterministic id for a record missing one on import, matching
 * BodyMetricsImporter.kt's `fileId`:
 *   UUID.nameUUIDFromBytes("file-$kind:${time.toEpochMilli()}".toByteArray())
 * i.e. an RFC 4122 v3 (name-based, MD5) UUID over the UTF-8 string
 * "file-<kind>:<epochMillis>", kind in {"weight","bodyfat","measure"}.
 * @param {"weight"|"bodyfat"|"measure"} kind
 * @param {number} epochMillis
 * @returns {Promise<string>}
 */
export async function deterministicFileId(kind, epochMillis) {
  const input = `file-${kind}:${epochMillis}`;
  const bytes = new TextEncoder().encode(input);
  const digest = md5(bytes);
  // RFC 4122 v3: version nibble -> 3, variant bits -> 10xxxxxx
  digest[6] = (digest[6] & 0x0f) | 0x30;
  digest[8] = (digest[8] & 0x3f) | 0x80;
  const hex = [...digest].map((b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

/**
 * Pure-JS MD5 (RFC 1321). SubtleCrypto does not implement MD5 (it's not
 * part of the Web Crypto spec at all, legacy or otherwise), so this can't
 * be delegated to the platform.
 * @param {Uint8Array} bytes
 */
function md5(bytes) {
  // RFC 1321 reference implementation, operating on the raw byte array.
  const s = [
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
  ];
  const K = new Int32Array(64);
  for (let i = 0; i < 64; i++) K[i] = (Math.floor(Math.abs(Math.sin(i + 1)) * 2 ** 32)) | 0;

  let a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;

  const bitLen = bytes.length * 8;
  let padLen = ((bytes.length + 8) >> 6) * 64 + 64;
  const msg = new Uint8Array(padLen);
  msg.set(bytes);
  msg[bytes.length] = 0x80;
  const view = new DataView(msg.buffer);
  view.setUint32(padLen - 8, bitLen >>> 0, true);
  view.setUint32(padLen - 4, Math.floor(bitLen / 2 ** 32), true);

  const rotl = (x, c) => (x << c) | (x >>> (32 - c));

  for (let chunk = 0; chunk < padLen; chunk += 64) {
    const M = new Int32Array(16);
    for (let j = 0; j < 16; j++) M[j] = view.getInt32(chunk + j * 4, true);

    let [A, B, C, D] = [a0, b0, c0, d0];
    for (let i = 0; i < 64; i++) {
      let F, g;
      if (i < 16) { F = (B & C) | (~B & D); g = i; }
      else if (i < 32) { F = (D & B) | (~D & C); g = (5 * i + 1) % 16; }
      else if (i < 48) { F = B ^ C ^ D; g = (3 * i + 5) % 16; }
      else { F = C ^ (B | ~D); g = (7 * i) % 16; }
      F = (F + A + K[i] + M[g]) | 0;
      A = D; D = C; C = B;
      B = (B + rotl(F, s[i])) | 0;
    }
    a0 = (a0 + A) | 0; b0 = (b0 + B) | 0; c0 = (c0 + C) | 0; d0 = (d0 + D) | 0;
  }

  const out = new Uint8Array(16);
  const outView = new DataView(out.buffer);
  outView.setInt32(0, a0, true);
  outView.setInt32(4, b0, true);
  outView.setInt32(8, c0, true);
  outView.setInt32(12, d0, true);
  return out;
}

/** @param {number|null} n @param {number} [dp] */
function round(n, dp = 1) {
  if (n == null) return null;
  const m = 10 ** dp;
  return Math.round(n * m) / m;
}

const MEASUREMENT_FIELDS = [
  ["neck_cm", "neckCm"], ["waist_cm", "waistCm"], ["hips_cm", "hipsCm"], ["chest_cm", "chestCm"],
  ["upper_arm_cm", "upperArmCm"], ["thigh_cm", "thighCm"], ["calf_cm", "calfCm"], ["wrist_cm", "wristCm"],
];

/**
 * @param {{weights: import('./models.js').WeightEntry[], bodyFat: import('./models.js').BodyFatEntry[], measurements: import('./models.js').BodyMeasurement[]}} input
 */
export function exportBodyMetrics({ weights, bodyFat, measurements }) {
  return {
    export: { app: "Chompass", kind: BODY_METRICS_KIND, format_version: BODY_METRICS_FORMAT_VERSION },
    weights: weights.map((w) => ({ id: w.id, date: w.date, weight_kg: round(w.weightKg, 2) })),
    body_fat: bodyFat.map((b) => ({ id: b.id, date: b.date, body_fat_percent: round(b.bodyFatPercent, 1) })),
    measurements: measurements.map((m) => {
      const wire = { id: m.id, date: m.date };
      for (const [wireKey, modelKey] of MEASUREMENT_FIELDS) wire[wireKey] = round(m[modelKey] ?? null);
      return wire;
    }),
  };
}

export class UnsupportedFormatError extends Error {}

/**
 * @param {any} doc
 * @returns {Promise<{weights: import('./models.js').WeightEntry[], bodyFat: import('./models.js').BodyFatEntry[], measurements: import('./models.js').BodyMeasurement[]}>}
 */
export async function importBodyMetrics(doc) {
  const exp = doc?.export;
  if (!exp || typeof exp.app !== "string") throw new UnsupportedFormatError("missing export.app");
  const app = exp.app.trim().toLowerCase();
  if (app !== "chompass" && app !== "nofud" && app !== "fud ai") throw new UnsupportedFormatError(`unrecognized app "${exp.app}"`);
  if (exp.kind !== BODY_METRICS_KIND) throw new UnsupportedFormatError(`unrecognized kind "${exp.kind}"`);
  if (exp.format_version !== BODY_METRICS_FORMAT_VERSION) {
    throw new UnsupportedFormatError(`unsupported format_version "${exp.format_version}"`);
  }

  const withId = async (rec, kind) => {
    if (rec.id) return rec.id;
    const epochMillis = Date.parse(rec.date);
    return deterministicFileId(kind, epochMillis);
  };

  const weights = [];
  for (const w of doc.weights ?? []) {
    weights.push({ id: await withId(w, "weight"), date: w.date, weightKg: w.weight_kg });
  }
  const bodyFat = [];
  for (const b of doc.body_fat ?? []) {
    bodyFat.push({ id: await withId(b, "bodyfat"), date: b.date, bodyFatPercent: b.body_fat_percent });
  }
  const measurements = [];
  for (const m of doc.measurements ?? []) {
    const entry = { id: await withId(m, "measure"), date: m.date };
    for (const [wireKey, modelKey] of MEASUREMENT_FIELDS) entry[modelKey] = m[wireKey] ?? null;
    measurements.push(entry);
  }

  return { weights, bodyFat, measurements };
}
