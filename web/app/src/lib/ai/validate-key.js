// @ts-check
/** Lightweight Gemini / Google AI Studio key probe (lists models; no generateContent). */
import { t } from "../i18n/index.js";

const GEMINI_MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models";

/**
 * @typedef {{ ok: true } | { ok: false, message: string }} ValidateKeyResult
 */

/**
 * @param {string} apiKey
 * @returns {Promise<ValidateKeyResult>}
 */
export async function validateGeminiApiKey(apiKey) {
  const key = String(apiKey || "").trim();
  if (!key) return { ok: false, message: t("errors.paste_key_first") };

  let res;
  try {
    res = await fetch(GEMINI_MODELS_URL, {
      method: "GET",
      headers: { "x-goog-api-key": key },
    });
  } catch {
    return { ok: false, message: t("errors.network_check") };
  }

  if (res.ok) return { ok: true };

  if (res.status === 400 || res.status === 401 || res.status === 403) {
    return { ok: false, message: "That API key was rejected. Create a new key at aistudio.google.com/apikey and paste it again." };
  }

  const detail = await res.text().catch(() => "");
  return {
    ok: false,
    message: detail?.trim()
      ? `Could not verify key (HTTP ${res.status}).`
      : `Could not verify key (HTTP ${res.status}). Try again in a moment.`,
  };
}
