// @ts-check
/**
 * Share-first file export for Safari/iOS (Save to Files / AirDrop),
 * with `<a download>` fallback for Chromium/desktop and when share is declined.
 */

/**
 * Trigger a classic download via an object URL.
 * @param {Blob} blob
 * @param {string} filename
 */
function anchorDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * @param {BlobPart} data
 * @param {string} filename
 * @param {string} mime
 * @param {string} [title]
 * @returns {Promise<"shared"|"downloaded">}
 */
export async function downloadOrShare(data, filename, mime, title = "NoFUD export") {
  const blob = data instanceof Blob ? data : new Blob([data], { type: mime });
  const file = new File([blob], filename, { type: mime || blob.type || "application/octet-stream" });

  const nav = navigator;
  if (typeof nav.canShare === "function" && typeof nav.share === "function") {
    try {
      if (nav.canShare({ files: [file] })) {
        await nav.share({ files: [file], title });
        return "shared";
      }
    } catch (err) {
      // User cancelled the share sheet — do not force a download.
      if (err && typeof err === "object" && "name" in err && /** @type {{name?: string}} */ (err).name === "AbortError") {
        return "shared";
      }
      // Fall through to anchor download for other errors / unsupported payloads.
    }
  }

  anchorDownload(blob, filename);
  return "downloaded";
}

/**
 * @param {unknown} doc
 * @param {string} filename
 * @returns {Promise<"shared"|"downloaded">}
 */
export function downloadJson(doc, filename) {
  return downloadOrShare(JSON.stringify(doc, null, 2), filename, "application/json");
}

/**
 * @param {string} text
 * @param {string} filename
 * @param {string} mime
 * @returns {Promise<"shared"|"downloaded">}
 */
export function downloadText(text, filename, mime) {
  return downloadOrShare(text, filename, mime);
}
