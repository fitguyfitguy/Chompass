// @ts-check
// BYOK provider key storage. The AES-GCM key that actually encrypts each
// provider's API key is generated non-extractable and stored as a CryptoKey
// object directly in IndexedDB (structured-clonable) — no JS code path, ours
// or an XSS payload's, can ever read its raw bytes back out. Only the
// decrypt operation (which yields the provider key, not the wrapping key)
// is reachable.
import { keys as keysStore } from "../db.js";

const MASTER_KEY_ID = "master-key";

/** @typedef {"anthropic"|"gemini"|"openai_compatible"} ProviderId */

async function getOrCreateMasterKey() {
  const record = await keysStore.get(MASTER_KEY_ID);
  if (record?.cryptoKey) return record.cryptoKey;
  const cryptoKey = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
  await keysStore.put({ id: MASTER_KEY_ID, cryptoKey });
  return cryptoKey;
}

/**
 * @param {ProviderId} provider
 * @param {string} apiKey
 * @param {{model?: string, baseUrl?: string}} [extra] extra non-secret config (model id, custom base URL for openai_compatible)
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

/** @param {ProviderId} provider @returns {Promise<{apiKey: string, provider: ProviderId, model?: string, baseUrl?: string}|null>} */
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
