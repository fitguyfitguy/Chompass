// @ts-check
// BYOK provider key storage. The AES-GCM key that actually encrypts each
// provider's API key is generated non-extractable and stored as a CryptoKey
// object directly in IndexedDB (structured-clonable) — no JS code path, ours
// or an XSS payload's, can ever read its raw bytes back out. Only the
// decrypt operation (which yields the provider key, not the wrapping key)
// is reachable.
//
// Safari Private / restricted storage sometimes rejects non-extractable
// CryptoKey persistence. In that case we fall back to an extractable key
// (CryptoKey in IDB, or JWK if the CryptoKey object still cannot be stored).
import { keys as keysStore } from "../db.js";

const MASTER_KEY_ID = "master-key";

/** @typedef {"anthropic"|"gemini"|"openai_compatible"} ProviderId */

/**
 * @param {JsonWebKey} jwk
 * @returns {Promise<CryptoKey>}
 */
async function importMasterJwk(jwk) {
  return crypto.subtle.importKey("jwk", jwk, { name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"]);
}

/**
 * Persist a master key, preferring non-extractable CryptoKey storage.
 * @returns {Promise<CryptoKey>}
 */
async function createAndStoreMasterKey() {
  // Preferred: non-extractable key object in IndexedDB.
  try {
    const cryptoKey = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
    await keysStore.put({ id: MASTER_KEY_ID, cryptoKey });
    return cryptoKey;
  } catch {
    /* restricted storage — try extractable paths */
  }

  // Fallback 1: extractable CryptoKey still stored as a structured-cloneable object.
  try {
    const cryptoKey = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"]);
    await keysStore.put({ id: MASTER_KEY_ID, cryptoKey });
    return cryptoKey;
  } catch {
    /* IDB may still refuse CryptoKey — use JWK */
  }

  // Fallback 2: export JWK and store plain JSON (weaker against XSS; still AES-GCM wraps provider keys).
  const cryptoKey = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"]);
  const jwk = await crypto.subtle.exportKey("jwk", cryptoKey);
  await keysStore.put({ id: MASTER_KEY_ID, jwk });
  return cryptoKey;
}

async function getOrCreateMasterKey() {
  const record = await keysStore.get(MASTER_KEY_ID);
  if (record?.cryptoKey) return record.cryptoKey;
  if (record?.jwk) return importMasterJwk(record.jwk);
  return createAndStoreMasterKey();
}

/**
 * @param {ProviderId} provider
 * @param {string} apiKey
 * @param {{model?: string, baseUrl?: string, visionModel?: string}} [extra] extra non-secret config (model id, custom base URL for openai_compatible, vision-model slot)
 */
export async function saveProviderKey(provider, apiKey, extra = {}) {
  const cryptoKey = await getOrCreateMasterKey();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    cryptoKey,
    new TextEncoder().encode(apiKey)
  );
  await keysStore.put({
    id: `provider:${provider}`,
    provider,
    iv: Array.from(iv),
    ciphertext: Array.from(new Uint8Array(ciphertext)),
    ...extra,
  });
}

/** @param {ProviderId} provider @returns {Promise<{apiKey: string, provider: ProviderId, model?: string, baseUrl?: string, visionModel?: string}|null>} */
export async function loadProviderKey(provider) {
  const record = await keysStore.get(`provider:${provider}`);
  if (!record) return null;
  const cryptoKey = await getOrCreateMasterKey();
  const iv = new Uint8Array(record.iv);
  const ciphertext = new Uint8Array(record.ciphertext).buffer;
  const plaintext = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, cryptoKey, ciphertext);
  return { apiKey: new TextDecoder().decode(plaintext), provider: record.provider, model: record.model, baseUrl: record.baseUrl };
}

/** @param {ProviderId} provider */
export async function deleteProviderKey(provider) {
  await keysStore.delete(`provider:${provider}`);
}

/** @returns {Promise<ProviderId[]>} providers with a stored key, without decrypting them */
export async function listConfiguredProviders() {
  const all = await keysStore.all();
  return all.filter((r) => r.id.startsWith("provider:")).map((r) => r.provider);
}
